<a id="back-to-top"></a>

# Frame và lời gọi

## Menu
- [Mỗi lời gọi tạo frame: biến cục bộ, operand stack và liên kết động](#stack-frames-and-calls-001)
- [Truyền đối số, vị trí this và lưu giá trị trả về](#stack-frames-and-calls-002)
- [invokestatic, invokespecial, invokevirtual và invokeinterface](#stack-frames-and-calls-003)
- [Phân giải phương thức và chọn phương thức theo kiểu thực của đối tượng](#stack-frames-and-calls-004)
- [invokedynamic, BootstrapMethods và liên kết điểm gọi động](#stack-frames-and-calls-005)
- [&lt;init&gt; và &lt;clinit&gt;: phương thức đặc biệt có vai trò khác nhau](#stack-frames-and-calls-006)
- [return và các biến thể khi lời gọi hoàn thành bình thường](#stack-frames-and-calls-007)
- [athrow, bảng xử lý ngoại lệ và tháo frame khi lời gọi thất bại](#stack-frames-and-calls-008)
- [Ánh xạ lambda và nối chuỗi xuống bytecode theo phiên bản trình biên dịch](#stack-frames-and-calls-009)

## <a id="stack-frames-and-calls-001">Mỗi lời gọi tạo frame: biến cục bộ, operand stack và liên kết động</a>

<details>
<summary>Xem chi tiết</summary>

# Mỗi lời gọi tạo frame: biến cục bộ, operand stack và liên kết động

## 1. Hiểu frame là gì và tại sao mỗi lời gọi cần một frame riêng

Khi mã Java gọi một phương thức, JVM cần nhớ **các tham số và biến cục bộ của lần gọi đó**, nơi đặt các giá trị trung gian trong lúc tính toán, cách tra cứu những tham chiếu đến class/field/method và nơi tiếp tục khi phương thức kết thúc. Theo **Java Virtual Machine Specification (JVMS) `2.6`**, mỗi lần gọi phương thức tạo một **frame** mới theo mô hình JVM, và frame đó tồn tại cho đến khi lần gọi hoàn tất bình thường hoặc hoàn tất bất thường do exception.

Một frame chứa ba thành phần cốt lõi:

1. **Local variables array**: các ô đánh số từ 0 để lưu `this` (với phương thức instance), tham số và giá trị cục bộ/tạm.
2. **Operand stack**: ngăn xếp LIFO để nạp giá trị, tính toán, chuẩn bị đối số, nhận kết quả của lời gọi phương thức.
3. **Tham chiếu tới run-time constant pool** của class/interface định nghĩa phương thức hiện tại: hỗ trợ **dynamic linking** từ symbolic references trong class file tới các thành phần runtime.

Frame còn tham gia chuyển **giá trị trả về**, tiếp tục điểm thực thi của caller và xử lý **exception**. JVM có thể lưu thêm thông tin riêng của triển khai; **ba thành phần trên là mô hình đặc tả**, không buộc HotSpot hoặc một JVM khác phải đặt từng frame thành một khối native memory riêng với layout giống sơ đồ sách.

### Minh họa một chuỗi lời gọi

~~~text
Một Java thread, theo thứ tự thời gian:

main() bắt đầu
  Thread's JVM stack:
  ┌─────────────────────────────────────┐
  │ frame(main)    <-- current frame    │
  └─────────────────────────────────────┘

main() gọi calculate(4)
  ┌─────────────────────────────────────┐
  │ frame(calculate) <-- current frame  │
  ├─────────────────────────────────────┤
  │ frame(main)                         │
  └─────────────────────────────────────┘

calculate(4) gọi add(4, 4)
  ┌─────────────────────────────────────┐
  │ frame(add)   <-- current frame      │
  ├─────────────────────────────────────┤
  │ frame(calculate)                    │
  ├─────────────────────────────────────┤
  │ frame(main)                         │
  └─────────────────────────────────────┘

add() trả 8 -> frame(add) kết thúc.
calculate() nhận 8, cộng 1, trả 9 -> frame(calculate) kết thúc.
main() tiếp tục với giá trị 9.
~~~

**Mỗi lần gọi là một frame khác nhau**, kể cả khi gọi lại cùng một phương thức. Trong một thread, chỉ frame của phương thức **đang thực thi** là current frame theo mô hình JVMS; khi gọi phương thức khác, frame của caller tạm ngừng và frame của callee trở thành current.

**Không nhầm** JVM stack (chứa các frame của một thread) với **operand stack** (nằm **bên trong từng frame**). Cũng không nhầm với **heap**, nơi các object/array thường được cấp phát theo mô hình JVM. Một local variable chứa `reference` có thể tham chiếu object trên heap; nó **không phải** bản sao toàn bộ object.

## 2. JVM stack và vòng đời của frame

Theo **JVMS `2.5.2`**, mỗi JVM thread có **JVM stack riêng**, được tạo khi thread hình thành. Các frame của một thread không được thread khác trực tiếp tham chiếu hay chia sẻ. Các thread có thể dùng chung object trên heap, nhưng **local variables và operand stack của từng frame** thuộc lần thực thi cụ thể của thread đó.

Quy trình trừu tượng khi một Java method gọi một Java method khác:

1. Caller tính các đối số trên operand stack của **frame caller**.
2. Lệnh invoke được thực hiện; JVM xác định method cần gọi theo ngữ nghĩa bytecode và tạo **frame cho lần gọi mới**.
3. Đối số (và receiver `this` nếu là instance method) được đưa vào **local variables của frame callee**. Operand stack của frame mới ban đầu **rỗng**.
4. Callee thực thi các bytecode, thao tác trên locals/operand stack của **chính frame callee**.
5. Nếu callee `return`/`ireturn`/`areturn`… bình thường, frame callee kết thúc; kết quả nếu có được chuyển về operand stack của caller và caller tiếp tục tại lệnh sau invoke.
6. Nếu exception không được xử lý trong callee, callee kết thúc **bất thường**; JVM tìm handler thích hợp bằng cơ chế exception dispatch và có thể tiếp tục tháo các frame trước đó.

Một frame được giải phóng theo **kết thúc lần gọi**, không theo phạm vi ngoặc nhọn của một khối `if`/`for`. Vòng lặp trong **một** phương thức thường dùng lại **một frame**, còn mỗi lời gọi đệ quy tạo thêm một frame trừu tượng.

### Mô hình tuần tự, không phải layout vật lý tuyệt đối

JVMS cho phép triển khai tối ưu hóa cách cấp phát và quản lý frame. Chẳng hạn JIT có thể **inline** một phương thức, tránh tạo một frame native độc lập tương ứng ở thời điểm thực thi mã máy, miễn giữ ngữ nghĩa chương trình và bảo đảm quan sát thích hợp. Stack trace, debug và deoptimization chịu ảnh hưởng của các cơ chế runtime. Khi nói “mỗi lời gọi tạo frame”, ta đang mô tả **hợp đồng máy ảo trừu tượng**.

JVM stack theo JVMS có thể có kích cỡ cố định hoặc khả năng mở rộng. Một chuỗi lời gọi quá sâu có thể gây `StackOverflowError`; nếu triển khai không thể cấp phát/mở rộng stack theo nhu cầu, có tình huống gây `OutOfMemoryError`. Những cơ chế native stack, `-Xss`, layout frame và tối ưu JIT là chi tiết của runtime cụ thể, không phải các local slots được Java source điều khiển trực tiếp.

## 3. Local variables array: slot 0, tham số và biến cục bộ

Mỗi frame có mảng **local variables** dài `max_locals` slots theo code của method. Slot được đánh số từ **0**; các bytecode `iload_0`, `iload_1`, `istore_2`, `aload_0`… tham chiếu chỉ số trong **frame hiện tại**.

### Static method và instance method

| Loại method | Slot đầu | Các slot tiếp theo |
| --- | --- | --- |
| `static int sum(int a, int b)` | `slot 0 = a` | `slot 1 = b`; các biến cục bộ bắt đầu sau tham số |
| `int sum(int a, int b)` (instance) | `slot 0 = this` | `slot 1 = a`, `slot 2 = b`; rồi biến cục bộ |
| Constructor `MyClass(int n)` | `slot 0 = this` | `slot 1 = n`; sau đó local/tạm |

**`this` không phải một biến riêng bắt buộc ở slot 0 của mọi method.** Static method không có receiver instance; slot 0 của nó thường chứa tham số đầu tiên. Constructor là một loại instance initialization method, có receiver ở slot 0 nhưng đối tượng đang qua quá trình khởi tạo cần tuân thủ các quy tắc đặc biệt của verifier.

Khi method có nhiều tham số, JVM chuyển chúng vào các slot liên tiếp theo **độ rộng kiểu**, không theo số lượng tên biến:

- `int`, `float`, `reference` (và các giá trị thuộc nhóm một slot) dùng **1 slot**.
- `long` và `double` dùng **2 slot liên tiếp**. Bytecode truy cập giá trị này từ **chỉ số thấp hơn**; không được `lload` từ slot thứ hai.
- Một giá trị `long` ở local slot 1 chiếm slot **1 và 2**; tham số tiếp theo bắt đầu tại **slot 3**.
- Slot thứ hai của `long`/`double` không phải một biến Java độc lập của cùng giá trị; việc ghi đè một slot có thể làm mất hiệu lực nội dung giá trị hai-slot.

Sơ đồ hai phương thức:

~~~java
static int add(int a, int b) {
    int sum = a + b;
    return sum;
}

long blend(long base, double factor, int delta) {
    long total = base + delta;
    return total + (long) factor;
}
~~~

Đây là **các method minh họa phải nằm bên trong một class**, không phải file Java hoàn chỉnh chạy độc lập.

~~~text
add(int a, int b) — static:
  slot 0: a             int
  slot 1: b             int
  slot 2: sum           int             (compiler thường dùng)

blend(long base, double factor, int delta) — instance:
  slot 0: this          reference
  slot 1: base          long (slot 1 + slot 2)
  slot 2: phần thứ hai của base
  slot 3: factor        double (slot 3 + slot 4)
  slot 4: phần thứ hai của factor
  slot 5: delta         int
  slot 6: total         long (slot 6 + slot 7)
  slot 7: phần thứ hai của total
~~~

**Không nhất thiết slot nào cũng mang đúng một tên biến Java suốt cả method.** Compiler có thể dùng slot cho giá trị tạm, tái sử dụng slot khi phạm vi sống khác nhau và tối ưu code. `max_locals` đo **dung lượng slot cần cấp cho frame**, không đếm số biến được khai báo trong source. Compiler có thể chọn layout khác trong những trường hợp phức tạp.

### LocalVariableTable khác local variables runtime

Mảng locals là thành phần runtime theo JVMS. `LocalVariableTable` là **debug attribute tùy chọn** trong class file, giúp ánh xạ slot sang tên biến và phạm vi BCI. Nếu biên dịch bằng `javac -g:none`, tên local có thể không xuất hiện trong `javap -l` nhưng JVM vẫn có locals và vẫn thực thi `iload`/`istore` bình thường.

Không nhầm:

- **`max_locals`**: thông tin dung lượng locals bắt buộc khi method có `Code`.
- **`LocalVariableTable`**: metadata debug tùy chọn.
- **StackMapTable**: metadata **verification type frames**, không phải bảng chứa tất cả frame runtime đang gọi nhau.

## 4. Operand stack: nơi bytecode xử lý các giá trị trung gian

Operand stack của **mỗi frame** là ngăn xếp LIFO (**last in, first out**). Khi frame mới được tạo, operand stack bắt đầu **trống**. Các bytecode sau đó:

- Đẩy hằng: `iconst_1`, `bipush 10`, `ldc`…
- Nạp locals: `iload_n`, `aload_n`, `lload_n`…
- Đọc field: `getstatic`, `getfield`…
- Lấy các toán hạng khỏi đỉnh stack, tính toán rồi đẩy kết quả: `iadd`, `imul`, `dadd`…
- Cất kết quả vào locals: `istore_n`, `astore_n`…
- Chuẩn bị receiver/arguments cho invoke; nhận giá trị trả về nếu callee trả một giá trị.
- Dùng một giá trị để branch (`ifeq`, `if_icmpgt`…), lưu field, trả về (`ireturn`) hoặc ném exception (`athrow`).

### Theo dõi phép cộng

Với `add(int a, int b)`, một compiler như `javac` có thể sinh chuỗi bytecode tương đương:

~~~text
0: iload_0
1: iload_1
2: iadd
3: istore_2
4: iload_2
5: ireturn
~~~

Đây là **disassembly minh họa hợp lý**. Bytecode offsets/biến thể có thể khác theo compiler, nhưng ngữ nghĩa các lệnh là cố định. Nếu `a=4`, `b=4`:

| Opcode | Locals liên quan | Operand stack sau lệnh (trái: đáy; phải: đỉnh) |
| --- | --- | --- |
| Vào method | `slot0=4, slot1=4` | `[]` |
| `iload_0` | Đọc `a` | `[4]` |
| `iload_1` | Đọc `b` | `[4, 4]` |
| `iadd` | Không đổi locals | `[8]` |
| `istore_2` | Ghi `sum=8` | `[]` |
| `iload_2` | Đọc `sum` | `[8]` |
| `ireturn` | Trả 8, frame kết thúc | Giá trị 8 chuyển về **operand stack của caller** |

Khi `iadd` chạy, nó **pop hai giá trị kiểu `int`** rồi push một giá trị `int`. Không nên tưởng tượng operand stack là một `java.util.Stack` do ứng dụng tạo ra; đó là cơ chế máy ảo phục vụ thực thi bytecode.

### Chiều sâu stack không đồng nghĩa số phần tử

`max_stack` đo độ sâu tối đa bằng **đơn vị slot**. `long` và `double` mỗi giá trị đóng góp **hai đơn vị** độ sâu; các loại category 1 đóng góp một.

Ví dụ khi bytecode đang giữ trên stack:

~~~text
[reference, long, int]

Số giá trị logic: 3
Độ sâu tính theo slot: 1 + 2 + 1 = 4
~~~

Vì vậy `max_stack=4` không có nghĩa method gọi bốn method khác hay JVM có bốn frame. Đó là độ sâu tối đa của **operand stack bên trong một frame** theo thông tin `Code`.

### Quy tắc kiểu và thao tác stack

Các lệnh bytecode có ràng buộc kiểu:

- `iadd` cần hai giá trị `int` tương thích.
- `ladd` cần hai giá trị `long` tương thích.
- `swap` chỉ hoán đổi các giá trị **category 1** thích hợp; không được chia đôi giá trị `long`/`double`.
- `dup`/`dup2` có các dạng sử dụng tùy category của các giá trị phía trên.
- `checkcast`, `aastore`, lời gọi method và các lệnh khác có ràng buộc riêng.

Verifier kiểm tra cấu trúc và tính hợp lệ của luồng kiểu theo JVMS. Không được lấy một `long` từ hai `int` tùy tiện chỉ vì tổng dung lượng chiếm hai slot.

## 5. Liên kết động: frame dùng run-time constant pool ra sao?

Khái niệm thứ ba thường ít rõ ràng hơn locals và operand stack là **dynamic linking**. Theo **JVMS `2.6.3`**, mỗi frame giữ một tham chiếu tới **run-time constant pool của class/interface chứa method hiện tại**. Code trong class file có thể chứa các **symbolic references** tới class, field và method khác.

Ví dụ Java:

~~~java
static int twice(int value) {
    return add(value, value);
}
~~~

Một disassembly có thể thể hiện:

~~~text
0: iload_0
1: iload_0
2: invokestatic #NN  // Method add:(II)I
5: ireturn
~~~

`#NN` là chỉ số constant pool **của class chứa `twice`**; nó không phải địa chỉ máy, số dòng source hay slot locals. Constant-pool entry dẫn tới **tên owner, tên method và descriptor**. Khi cần, VM dùng cơ chế resolution để chuyển symbolic reference này sang thông tin runtime cụ thể.

~~~text
twice() bytecode:
   invokestatic #NN
          |
          v
run-time constant pool (của class định nghĩa twice)
   #NN -> Methodref Example.add:(II)I
          |
          | method resolution (khi cần)
          v
resolved runtime method / thông tin liên kết
          |
          v
invoke add(...) -> tạo frame(add)
~~~

Đây là mô hình khái niệm. **Resolution có thể được thực hiện sớm hoặc muộn** theo luật JVMS, và VM có thể cache kết quả; không có yêu cầu rằng mỗi lần `invokestatic` chạy lại phải tìm tên symbol từ đầu. Ngoài ra:

- **Resolution** trả lời tham chiếu biểu tượng tương ứng thành phần runtime nào; nó có thể cần load/link class liên quan và kiểm tra truy cập.
- **Dynamic dispatch** trả lời implementation nào được chọn khi gọi một method có thể override, chẳng hạn `invokevirtual`/`invokeinterface`. Nó liên quan nhưng **không đồng nhất** với việc resolve symbolic reference.
- **`invokedynamic`** là một opcode với cơ chế call site/bootstrap riêng; nó **không phải** tên khác của “dynamic linking có trong mọi frame”.

Vì vậy câu “frame chứa **tham chiếu tới run-time constant pool** để hỗ trợ liên kết động” chính xác hơn câu “mỗi frame chứa bản sao toàn bộ constant pool” hoặc “mỗi lời gọi đều phải tra bảng phương thức từ đầu”.

### Phân biệt runtime constant pool với operand stack

| Cơ chế | Chứa gì? | Dùng khi nào? |
| --- | --- | --- |
| **Constant pool / run-time constant pool** | Symbolic information, constants, resolved references phù hợp triển khai | Liên kết tới field/method/class và nạp hằng |
| **Local variables** | `this`, tham số, giá trị local/tạm của lần gọi | `iload`, `istore`, `aload`, `lstore`… |
| **Operand stack** | Các giá trị trung gian và đối số chờ thao tác | Arithmetic, branch, invoke, return |
| **JVM stack** | Chuỗi **frames** của thread | Quản lý những lần gọi đang hoạt động |

Đặt các khái niệm này đúng chỗ rất quan trọng khi đọc output `javap -c -v` và khi giải thích một lỗi linking/verification.

## 6. Chương trình chạy được Java 17+: quan sát lời gọi lồng nhau

Tạo file **`FrameTrace.java`**:

~~~java
public class FrameTrace {
    static int add(int left, int right) {
        int sum = left + right;
        return sum;
    }

    static int calculate(int value) {
        int doubled = add(value, value);
        return doubled + 1;
    }

    public static void main(String[] args) {
        int answer = calculate(4);
        System.out.println("result=" + answer);
    }
}
~~~

Dùng Java 17+:

~~~bash
javac -g FrameTrace.java
java FrameTrace
~~~

**Stdout chính xác:**

~~~text
result=9
~~~

Ta có `add(4, 4) = 8`, rồi `calculate(4) = 8 + 1 = 9`.

### Theo dõi từng frame

**Thời điểm A — vào `main`:**

~~~text
frame(main)
  locals: slot0 = args (String[])
  operand stack: []
  link: run-time constant pool của FrameTrace
~~~

**Thời điểm B — `main` gọi `calculate(4)`:**

~~~text
current frame = frame(calculate)
  locals: slot0 = value = 4
  operand stack: []
  link: run-time constant pool của FrameTrace

suspended caller frame(main)
  locals: slot0 = args
  program execution will continue after invokestatic calculate
~~~

**Thời điểm C — `calculate` chuẩn bị `add(value, value)`:**

~~~text
frame(calculate) trước invoke:
  iload_0  -> operand stack = [4]
  iload_0  -> operand stack = [4, 4]
  invokestatic add:(II)I

new frame(add):
  locals: slot0 = left = 4
          slot1 = right = 4
  operand stack: []       <-- mới tạo, không mang sang nguyên stack caller
  link: run-time constant pool của FrameTrace
~~~

**Thời điểm D — `add` trả về 8:**

~~~text
frame(add):
  tính sum = 8
  ireturn 8
  -> frame(add) kết thúc

frame(calculate) tiếp tục:
  operand stack sau invoke = [8]
  istore_1  -> local slot1 = doubled = 8; stack = []
  iload_1, iconst_1, iadd -> [9]
  ireturn 9
  -> frame(calculate) kết thúc
~~~

**Thời điểm E — `main` tiếp tục:**

~~~text
frame(main) sau calculate(4):
  operand stack = [9]
  istore_1 -> slot1 = answer = 9
  ...
  System.out.println("result=" + answer)
~~~

Dòng cuối in `result=9`. Cùng một **run-time constant pool** của class `FrameTrace` có thể được các frame khác nhau **tham chiếu**, nhưng **locals và operand stack của mỗi frame là riêng**. Không có chuyện `add` vô tình ghi trực tiếp vào local `answer` của `main` chỉ vì cả hai cùng nằm trên JVM stack.

### Đọc bytecode thực tế để kiểm chứng

~~~bash
javap -c -p FrameTrace
javap -v -p FrameTrace
javap -l -p FrameTrace
~~~

Hãy tìm:

- `add`: `iload_0`, `iload_1`, `iadd`, `istore_2`, `iload_2`, `ireturn` (theo `javac` thông dụng).
- `calculate`: hai lệnh nạp `value`, `invokestatic` tới `add:(II)I`, lưu `doubled`, thêm 1, `ireturn`.
- `main`: `iconst_4` hoặc cách nạp hằng tương đương, `invokestatic` tới `calculate:(I)I`, lưu `answer` và code in `System.out`.
- Trong output `-v`, mỗi `Code` có `stack=...`, `locals=...` (tương ứng `max_stack` và `max_locals`), cùng constant-pool entries cho những lời gọi tham chiếu.
- `javac -g` cung cấp debug line/local information thường hiển thị bằng `LineNumberTable` và `LocalVariableTable`.

**Không giả định index `#N` hay offset BCI giống nhau trên mọi JDK.** Hãy đọc bytecode do chính compiler bạn vừa chạy tạo ra, rồi ghép `invokestatic #N` với entry `#N` trong constant pool của **cùng class file**.

## 7. `long` và `double`: vì sao local slot index nhảy cóc?

Ví dụ trên sử dụng `int` nên mỗi tham số chỉ chiếm một slot. Để thấy loại **category 2**, tạo file **`WideSlots.java`**:

~~~java
public class WideSlots {
    static long mix(long base, double adjustment, int extra) {
        long total = base + extra;
        return total + (long) adjustment;
    }

    public static void main(String[] args) {
        System.out.println("mixed=" + mix(10L, 2.9, 3));
    }
}
~~~

~~~bash
javac -g WideSlots.java
java WideSlots
javap -c -v -p WideSlots
~~~

**Stdout chính xác:**

~~~text
mixed=15
~~~

Phép tính: `10L + 3 = 13L`, `(long) 2.9 = 2L` vì phép ép Java cắt phần thập phân hướng về 0; tổng cuối là `15L`.

Đối với **static `mix(long,double,int)`**, các slot ban đầu:

| Slot | Nội dung | Ghi chú |
| --- | --- | --- |
| 0–1 | `base: long` | Hai slots, truy cập bằng chỉ số 0 |
| 2–3 | `adjustment: double` | Hai slots, truy cập bằng chỉ số 2 |
| 4 | `extra: int` | Một slot |
| 5–6 | `total: long` | Hai slots, dùng khi biến được lưu |

`max_locals` của method này theo bytecode compiler thông thường là ít nhất **7**; mọi phương án compiler phải đủ để phục vụ các slot được sử dụng. Các opcode dễ nhận ra gồm `lload_0`, `dload_2`, `iload` với index 4, `i2l`, `d2l`, `ladd`, `lstore` và `lreturn` (thứ tự tùy compiler). `lreturn` đưa một **giá trị long** về caller; không biến hai slots vật lý thành hai giá trị trả về độc lập.

Độ rộng slot chỉ là **mô hình lưu trữ JVM**, không phải lời hứa về căn chỉnh địa chỉ 64-bit. JVMS không bắt buộc `long`/`double` bắt đầu tại slot chẵn.

## 8. Đệ quy: mỗi lần gọi cùng một method vẫn có frame riêng

Đệ quy giúp nhìn rõ hơn tại sao JVM không thể dùng một bộ locals duy nhất cho mọi invocation.

Tạo file **`RecursiveFrames.java`**:

~~~java
public class RecursiveFrames {
    static int factorial(int n) {
        if (n <= 1) {
            return 1;
        }
        return n * factorial(n - 1);
    }

    public static void main(String[] args) {
        System.out.println("factorial(4)=" + factorial(4));
    }
}
~~~

~~~bash
javac RecursiveFrames.java
java RecursiveFrames
~~~

**Stdout chính xác:**

~~~text
factorial(4)=24
~~~

Theo mô hình JVM:

~~~text
main()
  factorial(4): local n = 4, chờ kết quả factorial(3)
    factorial(3): local n = 3, chờ kết quả factorial(2)
      factorial(2): local n = 2, chờ kết quả factorial(1)
        factorial(1): local n = 1 -> ireturn 1
      factorial(2): 2 * 1 -> ireturn 2
    factorial(3): 3 * 2 -> ireturn 6
  factorial(4): 4 * 6 -> ireturn 24
main(): in 24
~~~

Mỗi frame `factorial` có **giá trị `n` của lần gọi riêng**. Khi gọi sâu hơn, các frame trước giữ trạng thái chờ cho tới khi nhận giá trị trả về, sau đó tiếp tục. Nếu chỉ có một biến `n` toàn cục cho mọi lời gọi, tính toán trên sẽ không thể hoạt động đúng.

**Đệ quy không giới hạn có thể làm tràn stack.** Ví dụ `factorial(-1)` trong chương trình này trả 1 ngay do điều kiện `n <= 1`; vì vậy nó **không** phải ví dụ tràn stack. Một method gọi chính nó mãi không có điều kiện dừng sẽ tiêu tốn khả năng gọi lồng nhau và có thể ném `StackOverflowError`. Đừng cố tình tạo tràn stack trong môi trường production để đo giới hạn vì giới hạn thực tế phụ thuộc runtime và cấu hình.

### Tail call, inlining và frame vật lý

Không suy rằng “có n lời gọi source thì máy luôn cấp phát n khối native stack giống hệt nhau”. HotSpot/JIT có thể inline một số lời gọi. Java/JVMS không đưa ra bảo đảm chung rằng mọi tail-recursive method được chuyển thành vòng lặp và sẽ không tràn stack. Hãy dùng thuật toán lặp hoặc cấu trúc dữ liệu phù hợp khi cần kiểm soát độ sâu.

## 9. Hoàn tất lời gọi bình thường: đưa kết quả về caller

Theo **JVMS `2.6.4`**, method kết thúc bình thường khi bytecode chạy lệnh return thích hợp:

| Opcode | Kiểu kết quả |
| --- | --- |
| `return` | `void`, không đưa giá trị kết quả lên operand stack của caller |
| `ireturn` | `int` (gồm cách biểu diễn kết quả cho `boolean`, `byte`, `char`, `short` ở cấp JVM) |
| `lreturn` | `long` |
| `freturn` | `float` |
| `dreturn` | `double` |
| `areturn` | `reference` |

Khi callee hoàn tất bình thường:

1. Kết quả (nếu có) được **chuyển vào operand stack của caller**.
2. Callee frame được loại bỏ khỏi JVM stack trừ khi triển khai dùng cách biểu diễn tối ưu khác nhưng giữ ngữ nghĩa.
3. Caller trở lại current frame, tiếp tục sau lệnh invoke với trạng thái locals/operand stack của **caller**.

Ví dụ `int x = add(2, 3);`:

~~~text
caller trước invokestatic:
  operand stack = [2, 3]

callee add khi vừa vào:
  locals = [2, 3]
  operand stack = []

callee ireturn 5

caller sau invoke:
  operand stack = [5]

caller istore_<n>:
  local x = 5
  operand stack = []
~~~

Tuy nhiên khi lời gọi hoàn tất do **uncaught exception**, **không có kết quả method bình thường** được đẩy về caller. Cách kết thúc bất thường được xử lý riêng.

## 10. Exception: tìm handler và tháo frame

Theo **JVMS `2.6.5`**, nếu một instruction ném exception mà method hiện tại không xử lý, lần gọi kết thúc **abruptly**. JVM không chạy “nhánh `ireturn` giả định” rồi đưa một kết quả tùy ý cho caller. Exception có thể truyền qua nhiều frame cho đến khi tìm được handler phù hợp, nếu không được bắt thì ảnh hưởng thread và cơ chế xử lý uncaught exception.

Tạo file **`ExceptionalFrames.java`**:

~~~java
public class ExceptionalFrames {
    static void bottom() {
        throw new IllegalStateException("boom");
    }

    static void middle() {
        bottom();
    }

    static void top() {
        try {
            middle();
        } catch (IllegalStateException ex) {
            System.out.println("caught=" + ex.getMessage());
        }
    }

    public static void main(String[] args) {
        top();
        System.out.println("continued");
    }
}
~~~

~~~bash
javac -g ExceptionalFrames.java
java ExceptionalFrames
~~~

**Stdout chính xác:**

~~~text
caught=boom
continued
~~~

Luồng frame:

~~~text
main()
  top()
    middle()
      bottom()
        athrow IllegalStateException("boom")
      bottom frame kết thúc bất thường
    middle không có handler -> middle frame kết thúc bất thường
  top có handler IllegalStateException -> thực thi catch
  top kết thúc bình thường
main() tiếp tục in "continued"
~~~

Điều cần hiểu:

- `bottom()` không trả về một giá trị cho `middle()`; `middle()` cũng không hoàn tất theo đường bình thường.
- **Exception table** trong `Code` của `top` xác định vùng code được bảo vệ và handler; nó không phải chỉ là một bảng “tên exception” tĩnh của cả JVM.
- Khi VM chuyển vào handler, operand stack có trạng thái theo quy tắc exception handler (chứa exception object để bytecode xử lý), không phải giữ nguyên các giá trị trung gian ngẫu nhiên trước khi exception ném ra.
- `finally`, nếu có, vẫn tuân theo ngữ nghĩa Java riêng; đừng nhầm exception-unwinding với việc “bỏ qua tất cả finally”.

Bạn có thể xem:

~~~bash
javap -c -v -p ExceptionalFrames
~~~

Trong `top`, tìm `Exception table`, `astore` để cất exception vào local và các lời gọi `getMessage`/`println`. Hình dạng bytecode và chỉ số constant pool khác nhau theo compiler nhưng thứ tự xử lý trong ví dụ là xác định.

## 11. `max_stack` và `max_locals` nằm ở đâu trong class file?

Theo **JVMS `4.7.3`**, mỗi method có thân bytecode (không phải `abstract` hoặc `native`) có `Code` attribute:

~~~text
Code_attribute {
    u2 attribute_name_index;
    u4 attribute_length;
    u2 max_stack;
    u2 max_locals;
    u4 code_length;
    u1 code[code_length];
    u2 exception_table_length;
    ...
    u2 attributes_count;
    attribute_info attributes[attributes_count];
}
~~~

Hai trường quan trọng:

- **`max_stack`**: độ sâu tối đa của **operand stack** tính theo slot tại **bất kỳ điểm nào** trong quá trình thực thi method.
- **`max_locals`**: số slot của **local variables array**, bao gồm cả receiver, các tham số và locals/tạm.

**Các con số này xác định theo code method**, không phải đo được chính xác số frame đang hoạt động trong lúc JVM chạy. Chẳng hạn `stack=2, locals=3` trong output `javap -v` là hai kích thước **bên trong một frame** cho method đang xem.

Giá trị `max_stack` có thể khác số lệnh trong method và khác số tham số. Một method dài hàng trăm instruction nhưng chỉ cần stack depth 2 vẫn hợp lệ; một lời gọi có nhiều đối số có thể cần depth lớn hơn.

Bộ kiểm tra bytecode của JVM sử dụng các ràng buộc kiểu và dữ liệu class file để đảm bảo việc thao tác stack/locals hợp lệ. `StackMapTable` hỗ trợ xác minh ở các điểm control flow, nhưng không phải snapshot runtime của tất cả lời gọi đang chạy.

### `Code` cho method abstract/native?

`abstract` method không có thân `Code` để JVM thực thi. `native` method có phần thân ở ngôn ngữ/bộ phận native thông qua cơ chế triển khai, không được mô tả bằng `Code` attribute của Java bytecode. Vì vậy cách giải thích “mỗi lời gọi tạo frame với `Code.max_stack/max_locals`” áp dụng trực tiếp cho **những Java bytecode methods có Code**, còn native method invocation liên quan thêm native method stacks/ABI và chi tiết runtime. Điều này không làm mất ý nghĩa của mô hình method invocation trong JVMS.

## 12. So sánh frame, heap, static state và method area

| Thành phần | Phạm vi | Dữ liệu điển hình | Ví dụ trong bài |
| --- | --- | --- | --- |
| **JVM stack** | Riêng từng thread | Những frame của các lời gọi đang hoạt động | `main → calculate → add` |
| **Frame local variables** | Riêng mỗi lần gọi | `this`, tham số, local values và references | `left=4, right=4, sum=8` |
| **Frame operand stack** | Riêng mỗi lần gọi | Giá trị tính toán, đối số, kết quả chờ dùng | `[4,4] → [8]` |
| **Heap** | Chia sẻ trong JVM theo mô hình | Object và array instances | `new IllegalStateException(...)` |
| **Run-time constant pool** | Gắn với class/interface, nằm trong vùng lưu dữ liệu runtime tương ứng | Symbolic references, constants, link info | `Methodref FrameTrace.add:(II)I` |
| **Static fields** | Gắn với class theo runtime identity | Trạng thái class, phụ thuộc class loader | `static int counter` nếu chương trình khai báo |
| **PC register** | Riêng mỗi thread | Vị trí instruction hiện hành khi chạy Java method | BCI đang thực thi trong `add` |

Sơ đồ object/reference:

~~~text
frame(main).local[1] = reference ──────────┐
                                           v
                                        Heap object
                                           ^
frame(otherMethod).local[0] = reference ────┘
~~~

Hai frame có thể trỏ đến **cùng một object**, vì vậy sửa object qua reference từ thread này có thể ảnh hưởng thread kia nếu phối hợp thread-safety không đúng. Nhưng hai frame không tự chia sẻ **mảng locals** hay **operand stack** chỉ vì chúng tham chiếu cùng object.

Khi một frame hết vòng đời, các local references của frame đó không còn là references sống từ frame ấy. Điều này **không chứng minh** object liên quan được GC thu ngay lập tức: object vẫn có thể được tham chiếu ở nơi khác, GC diễn ra theo chính sách runtime, và JIT/liveness có thể ảnh hưởng thời gian object thực sự được coi là reachable. Đừng suy “frame pop = heap object bị giải phóng ngay”.

## 13. Đọc StackTrace mà không đồng nhất nó với operand stack

Một stack trace Java thường thể hiện **chuỗi lời gọi method**:

~~~text
java.lang.IllegalStateException: boom
    at ExceptionalFrames.bottom(ExceptionalFrames.java:3)
    at ExceptionalFrames.middle(ExceptionalFrames.java:7)
    at ExceptionalFrames.top(ExceptionalFrames.java:12)
    at ExceptionalFrames.main(ExceptionalFrames.java:19)
~~~

Đây là **sơ đồ minh họa call chain**, **số dòng không cam kết** nếu bạn thêm/bớt dòng trong file. Chương trình `ExceptionalFrames` ở mục 10 bắt exception và **không tự in stack trace**; muốn xem stack trace bạn có thể gọi `ex.printStackTrace()` trong catch, khi đó output có thêm thông tin và thường đi tới stderr.

Stack trace hiển thị những **frames quan sát được của lời gọi**, không in toàn bộ operand stack hoặc giá trị từng local slot. Một dòng như `at ...middle(...java:7)` giúp định vị lời gọi nhưng không có nghĩa “operand stack ở dòng 7 chứa 7 phần tử”.

Các công cụ như debugger, JFR hoặc `jcmd Thread.print` có thể hỗ trợ phân tích thread/call stack trên runtime cụ thể. Chúng không thay thế bản đặc tả về locals, operand stack và constant-pool linking. Để nhìn cấp bytecode và slot, `javap -c -v -l -p` là lựa chọn rõ hơn.

## 14. Lỗi thường gặp và cách sửa cách hiểu

| Hiểu nhầm | Giải thích đúng |
| --- | --- |
| “Mỗi Java method chỉ có một frame cho toàn bộ ứng dụng.” | **Mỗi lần gọi** method tạo frame trừu tượng; đệ quy có nhiều frame của cùng method. |
| “Một vòng lặp chạy 100 lần tức 100 frame.” | Không, vòng lặp trong cùng lần gọi thường tiếp tục trong **một frame**. |
| “JVM stack và operand stack là một.” | JVM stack chứa **frames**; mỗi frame chứa **operand stack** của riêng nó. |
| “Slot 0 luôn là `this`.” | Chỉ với instance method; static method dùng slot 0 cho tham số đầu nếu có. |
| “Một tham số = một local slot.” | `long`/`double` chiếm **hai** slot liên tiếp. |
| “`max_stack` là số method đang gọi lồng nhau.” | Đó là độ sâu **operand stack** của một method, không phải số frames. |
| “Callee dùng chính operand stack của caller.” | Callee bắt đầu với operand stack **riêng, rỗng**; arguments được chuyển vào locals callee. |
| “`ireturn` giữ lại frame callee để caller đọc local bên trong.” | Nó trả một **giá trị** về stack caller; frame callee hoàn tất. |
| “Exception cũng trả một giá trị qua `ireturn`.” | Exception không được xử lý trong callee gây **abrupt completion**, không return bình thường. |
| “Dynamic linking nghĩa mọi lời gọi đều quét toàn bộ constant pool.” | Resolution có thể lazy/cached; frame giữ **tham chiếu** tới run-time constant pool. |
| “Dynamic linking = dynamic dispatch = `invokedynamic`.” | Đây là các khái niệm liên quan nhưng **khác chức năng**. |
| “LocalVariableTable thiếu thì JVM không có locals.” | Runtime locals vẫn tồn tại; bảng debug chỉ chứa tên/phạm vi nếu compiler ghi. |
| “Frame biến mất thì mọi object từ frame bị xóa khỏi heap.” | Object có thể còn reachable qua references khác; GC không đồng nghĩa pop frame. |
| “JIT bắt buộc luôn cấp phát một native stack frame vật lý cho từng lời gọi.” | JVMS mô tả **frame trừu tượng**; triển khai có thể tối ưu hóa và inline. |

## 15. Bài tập tự kiểm tra

**Câu 1.** Trong `static int add(int x, int y)`, các tham số nằm ở slot nào?  
**Đáp án:** Thông thường `x` ở slot 0 và `y` ở slot 1; không có receiver `this`.

**Câu 2.** Trong `long instanceMethod(int a, long b, double c)`, local slot đầu của `b` và `c` là gì?  
**Đáp án:** `slot0=this`, `slot1=a`; `b` bắt đầu slot2 và chiếm slot2–3; `c` bắt đầu slot4 và chiếm slot4–5.

**Câu 3.** Nếu `main` gọi `calculate` và `calculate` gọi `add`, current frame khi đang tính `iadd` bên trong `add` là frame nào?  
**Đáp án:** `frame(add)`. Frames của `calculate` và `main` đang tạm ngừng chờ callee.

**Câu 4.** Sau `add(4,4)` kết thúc bằng `ireturn`, giá trị 8 xuất hiện ở đâu?  
**Đáp án:** Trên **operand stack của frame caller `calculate`**, rồi caller có thể lưu vào local `doubled`.

**Câu 5.** Vì sao `max_stack=4` không có nghĩa method có bốn frames?  
**Đáp án:** `max_stack` là độ sâu tối đa **theo slot của operand stack của một frame**.

**Câu 6.** `invokestatic #N` tham chiếu gì và có nhất thiết tìm lại symbol ở mỗi lần gọi không?  
**Đáp án:** `#N` là constant-pool index dẫn tới symbolic method reference; VM có thể resolve/cache theo luật, không cần tìm lại mỗi lần.

**Câu 7.** Hai lời gọi đệ quy `factorial(4)` và `factorial(3)` có chia sẻ local `n` không?  
**Đáp án:** Không. Mỗi invocation có frame/locals riêng với `n=4` hoặc `n=3`.

**Câu 8.** Nếu `bottom` ném exception mà không có handler, caller `middle` có nhận “kết quả method” gì không?  
**Đáp án:** Không. Lần gọi `bottom` kết thúc bất thường, exception tiếp tục tìm handler thay vì trả một giá trị qua `return`.

**Câu 9.** Vì sao thiếu `LocalVariableTable` không làm class file không chạy được?  
**Đáp án:** Vì đó là metadata debug tùy chọn; `Code.max_locals` cùng bytecode vẫn chỉ định đầy đủ slots cần cho thực thi.

**Câu 10.** Nêu ranh giới giữa method frame và object trên heap.  
**Đáp án:** Frame lưu locals/operand stack của **lần gọi**; reference trong frame có thể trỏ đến object trên heap. Hết frame không đồng nghĩa object bị thu hồi ngay.

## 16. Tài liệu chính thức và cách tự học tiếp

- [JVMS Java SE 25 — `2.5.2` Java Virtual Machine Stacks](https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-2.html#jvms-2.5.2): JVM stack riêng của từng thread, chứa frames.
- [JVMS Java SE 25 — `2.6` Frames](https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-2.html#jvms-2.6): mỗi lần invoke tạo frame, current frame, vòng đời và return.
- [JVMS Java SE 25 — `2.6.1` Local Variables](https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-2.html#jvms-2.6.1): slot, receiver, tham số, `long`/`double`.
- [JVMS Java SE 25 — `2.6.2` Operand Stacks](https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-2.html#jvms-2.6.2): LIFO, loại giá trị, max depth và category.
- [JVMS Java SE 25 — `2.6.3` Dynamic Linking](https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-2.html#jvms-2.6.3): liên kết symbolic references qua run-time constant pool.
- [JVMS Java SE 25 — `2.6.4 và `2.6.5](https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-2.html#jvms-2.6.4): hoàn tất bình thường, bất thường và vận chuyển kết quả/exception.
- [JVMS Java SE 25 — `4.7.3` Code Attribute](https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-4.html#jvms-4.7.3): `max_stack`, `max_locals`, bytecode và exception table.
- [JVMS Java SE 25 — `5` Linking, Loading, and Initialization](https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-5.html): resolution và invocation liên quan symbolic references.
- [JDK 25 `javap` manual](https://docs.oracle.com/en/java/javase/25/docs/specs/man/javap.html): cách dùng `-c`, `-v`, `-l` để đối chiếu locals, operand stack và Code metadata.

**Cách ghi nhớ:** Một **thread** có **JVM stack**; một lần **invoke** có một **frame**; frame có **local variables**, **operand stack** và tham chiếu tới **run-time constant pool**; khi callee hoàn tất, caller tiếp tục với giá trị trả về hoặc theo đường exception. Từ mô hình này, bạn có thể đọc `javap` mà không nhầm local slots, operand-stack depth, constant-pool index và chuỗi lời gọi method.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="stack-frames-and-calls-002">Truyền đối số, vị trí this và lưu giá trị trả về</a>

<details>
<summary>Xem chi tiết</summary>

# JVM Frame và lời gọi phương thức: truyền đối số, vị trí `this` và giá trị trả về

Khi một phương thức Java gọi phương thức khác, JVM phải chuyển các giá trị đối số từ ngữ cảnh **bên gọi** sang ngữ cảnh **bên được gọi**, xác định đối tượng nhận lời gọi `this` nếu có, sau đó đưa giá trị trả về về đúng nơi. Cơ chế được mô tả bằng **operand stack**, **local-variable array**, **frame** và các bytecode `invoke*` / `*return`.

Một sự phân biệt quan trọng:

- **Ở phía bên gọi (caller):** bytecode chuẩn bị `objectref` (nếu là instance call) và các giá trị đối số **trên operand stack của frame hiện tại**.
- **Khi lệnh gọi thực thi:** JVM xử lý lời gọi, lập frame mới cho phương thức Java đích theo mô hình đặc tả, gán receiver/đối số vào **local variables của frame mới**.
- **Trong bên được gọi (callee):** local slot `0` là `this` với instance method, nhưng là đối số đầu tiên với static method.
- **Khi trả về bình thường:** lệnh `ireturn`, `lreturn`, `freturn`, `dreturn`, `areturn` hoặc `return` kết thúc frame callee; nếu có giá trị, giá trị được chuyển về **operand stack của caller**.

Đây là **mô hình JVM ở cấp bytecode**. HotSpot có thể inline method, dùng thanh ghi CPU, loại bỏ cấp phát và tối ưu cách bố trí frame vật lý. Không nên lấy sơ đồ logic dưới đây làm cam kết mỗi lời gọi luôn tạo một native stack frame hữu hình.

## 1. Bức tranh tổng quát: hai frame riêng biệt

Giả sử source Java:

```java
static int add(int x, int y) {
    return x + y;
}

static int calculate() {
    int result = add(2, 3);
    return result * 10;
}
```

Đây là **các phương thức minh họa**, chưa phải class chạy độc lập. Ở mức bytecode, trình tự tư duy:

```text
Caller frame: calculate()                      Callee frame: add(int,int)
┌───────────────────────────────────┐         ┌──────────────────────────────┐
│ Local variables                   │         │ Local variables             │
│  slot 0: result (sau khi store)   │         │  slot 0: x = 2              │
│                                   │         │  slot 1: y = 3              │
│ Operand stack (ngay trước invoke) │         │ Operand stack: lúc bắt đầu   │
│  ... , int 2 , int 3              │──invoke►│  rỗng                       │
└───────────────────────────────────┘         └──────────────────────────────┘
                                                         │
                                                 iload_0, iload_1
                                                         │
                                                     iadd → 5
                                                         │
                                                     ireturn
                                                         ▼
Caller được tiếp tục:
  operand stack có int 5
  istore_0 → lưu result = 5
  iload_0, bipush 10, imul, ireturn → trả 50
```

**Hai operand stacks khác nhau:** khi `add()` bắt đầu, operand stack của frame mới **rỗng**; tham số đã trở thành **local variables** của frame `add`, không phải tiếp tục nằm nguyên trên stack operand của callee. Ngược lại, trước lệnh `invokestatic add`, caller phải đẩy đủ đối số theo đúng thứ tự.

Mỗi frame gắn với **một lời gọi phương thức cụ thể**. Nếu `add` gọi tiếp phương thức khác, một frame khác trở thành frame hiện tại; frame `add` tạm không còn là current frame cho đến khi lời gọi bên trong kết thúc.

## 2. Cấu trúc một frame trong JVM

Trong mô hình Java Virtual Machine Specification (JVMS), mỗi invocation của phương thức Java tạo một frame chứa:

| Thành phần frame | Nhiệm vụ |
| --- | --- |
| **Local-variable array** | Chứa receiver `this` nếu có, các tham số và biến cục bộ/tạm thời; truy cập qua index 0,1,2,... |
| **Operand stack** | Đưa toán hạng cho các lệnh bytecode, chuẩn bị đối số gọi method và nhận kết quả trả về |
| **Dynamic-linking information** | Hỗ trợ resolve các tham chiếu ký hiệu đến field/method/type trong runtime constant pool |
| **Thông tin trả về** | Phục vụ khôi phục luồng thực thi và chuyển kết quả về caller theo ngữ nghĩa VM |

Frame nằm trong ngữ cảnh **Java Virtual Machine stack của thread** gọi nó. Mỗi JVM thread có stack/frame logic riêng, nên biến cục bộ trong lời gọi của thread A không tự động là cùng local-variable array với lời gọi của thread B.

Thông số được ghi trong `Code` attribute của class file:

- `max_locals`: số **local slots** tối đa cần cho một frame của phương thức, bao gồm receiver, parameters và temporaries.
- `max_stack`: độ sâu tối đa tính theo đơn vị của **operand stack**.
- `code_length` và `code[]`: các bytes chứa chỉ thị bytecode của phương thức.

Hai vùng này độc lập; không thể dùng `max_stack` để suy số biến Java hay dùng `max_locals` để suy số object còn sống trên heap.

### 2.1. Khi tạo frame mới, operand stack ban đầu rỗng

Ở thời điểm phương thức Java mới bắt đầu, JVM đã đưa receiver/các giá trị đối số vào **local variables của callee**. Việc tính toán trong callee tiếp tục qua lệnh `iload`, `aload`, `dload`, `lload`,... để đưa local values lên operand stack của chính nó.

Ví dụ `add(int,int)` có bytecode minh họa:

```text
0: iload_0
1: iload_1
2: iadd
3: ireturn
```

Trong phương thức `static` đó:

- `iload_0` lấy local `0 = x` lên operand stack.
- `iload_1` lấy local `1 = y`.
- `iadd` pop hai `int` và push kết quả.
- `ireturn` pop một `int` từ operand stack callee, trả nó về caller.

Đừng nhầm local slot `0` của `add` với local slot `0` của `calculate`: đó là **hai frame khác nhau** dù có cùng chỉ số.

## 3. Truyền đối số: bên gọi dùng operand stack, bên nhận dùng local variables

### 3.1. Bước chuẩn bị ở caller

Với lời gọi `add(2, 3)` trong Java source, compiler tạo logic tương tự:

```text
iconst_2              // push argument 2
iconst_3              // push argument 3
invokestatic #...     // gọi add:(II)I
                      // kết quả int được push về operand stack caller
```

Operand stack là last-in-first-out, nhưng điều đó **không đảo thứ tự tham số**: compiler đặt các giá trị theo **thứ tự phù hợp với method descriptor**, và lệnh invoke biết cách lấy chúng. Với static call `add(int x, int y)`, `2` tương ứng `x`, `3` tương ứng `y`.

Khi các biểu thức đối số có side effects, Java Language Specification còn quy định thứ tự đánh giá expression. Trước khi gọi được thực hiện, biểu thức argument phải được đánh giá hợp lệ theo thứ tự ngôn ngữ Java, trừ những trường hợp lời gọi fail sớm theo quy tắc của biểu thức gọi (ví dụ ngoại lệ khi đánh giá receiver). Không được tự giả định đánh giá từ phải qua trái chỉ vì operand stack là LIFO.

### 3.2. Local variables của callee

Một phương thức **static** nhận tham số bắt đầu tại local index **0**:

```java
static int sum(int left, int right) {
    return left + right;
}
```

```text
Callee local variables
index 0 = left
index 1 = right
```

Một phương thức **instance** dành local index **0** cho receiver:

```java
int sum(int left, int right) {
    return left + right;
}
```

```text
Callee local variables
index 0 = this
index 1 = left
index 2 = right
```

**`this` không được viết tường minh trong method descriptor**. Đối với instance method, `this` là receiver được JVM truyền bằng một tham chiếu object. Một method có ba tham số source vẫn có thể dùng bốn local slots chỉ cho receiver và các tham số nếu cả ba đều category 1.

### 3.3. `long` và `double` chiếm hai local-variable slots

Quy tắc category 2 của JVM:

- `long` và `double`: mỗi giá trị dùng **hai local-variable slots liên tiếp**.
- `int`, `float`, tham chiếu object: thường dùng **một slot** theo mô hình JVM.
- `boolean`, `byte`, `short`, `char` thường được xử lý qua int-family bytecodes trong những thao tác được quy định.
- Một giá trị `long`/`double` chỉ được tải từ **index thấp hơn** của cặp slot. Index cao hơn không phải nửa giá trị có thể đọc riêng bằng `iload`.

Xét:

```java
static long staticWork(long base, double rate, int extra) {
    return base + (long) (rate * extra);
}

long instanceWork(long base, double rate, int extra) {
    return base + (long) (rate * extra);
}
```

Bảng:

| Phương thức | Slot 0 | Slot 1 | Slot 2 | Slot 3 | Slot 4 | Slot 5 |
| --- | --- | --- | --- | --- | --- | --- |
| `staticWork(JDI)J` | `base` (long) | phần 2 của `base` | `rate` (double) | phần 2 của `rate` | `extra` (int) | — |
| `instanceWork(JDI)J` | `this` | `base` (long) | phần 2 của `base` | `rate` (double) | phần 2 của `rate` | `extra` (int) |

Vậy:

- `staticWork` cần **ít nhất 5 local slots** cho parameters; `lload_0` tải `base` và `dload_2` tải `rate`.
- `instanceWork` cần **ít nhất 6 local slots** cho receiver và parameters; `lload_1` tải `base` và `dload_3` tải `rate`.
- Cả hai có **ba tham số Java**, dù số parameter slots khác nhau. `long` và `double` không được tính thành hai đối số nguồn.

Descriptor JVM của cả hai là **`(JDI)J`**, vì chúng nhận `long` (`J`), `double` (`D`), `int` (`I`) và trả `long` (`J`). Receiver `this` không nằm trong descriptor.

## 4. Receiver `this`: xuất hiện ở đâu và khi nào?

Một instance method cần object target. Trong Java source, receiver có thể hiện rõ hoặc ngầm định:

```java
item.process(5);    // receiver là item
process(5);         // trong instance method: receiver ngầm là this
this.process(5);    // receiver viết tường minh
```

Trong class file, khi method được gọi bằng cơ chế instance invocation thích hợp, **operand stack của caller có object reference**, và callee nhận reference đó ở **local slot 0**.

Ví dụ lời gọi `item.process(5)`:

```text
Caller operand stack trước lời gọi (đáy → đỉnh):
    ..., objectref(item), int(5)
                           ▲ đỉnh

invokevirtual process:(I)V
    │
    ▼
Callee frame của item.process:
    local 0 = objectref(item) → this
    local 1 = 5              → tham số
    operand stack = rỗng khi bắt đầu
```

Đặc biệt:

- **Static method không có receiver `this`.** Không thể dùng `this` trong thân static method như instance method.
- **Instance method có receiver ở local 0** theo quy tắc parameter passing, kể cả khi source không hề dùng từ khóa `this`.
- **Static nested class** vẫn có instance methods và `this` của **chính** nested class đó; “static” trong `static class Nested` không làm mọi method của nó trở thành static.
- **Constructor `<init>`** là một trường hợp instance initialization: receiver ở local 0 trong frame constructor. Trước khi object initialization hoàn thành, verifier áp dụng thêm ràng buộc về reference chưa được khởi tạo.
- **Instance synchronized method** cũng có receiver ở local 0; cơ chế monitor được JVM quản lý theo lời gọi, không cần tự viết `monitorenter` như với block `synchronized`.

### 4.1. `this` không phải là một object được copy nguyên khối

Java truyền **giá trị của reference**. Khi callee nhận receiver, nó không tạo một object mới giống hệt receiver. Caller và callee đều có reference tới **cùng object**; thao tác trên field của object đó có thể quan sát được bên ngoài khi được phép.

Vì thế:

```java
void setValue(int next) {
    this.value = next;
}
```

Thay field `this.value` tác động tới cùng object mà caller đang giữ. Điều đó **không phải** “Java truyền object theo tham chiếu” theo nghĩa có thể gán lại biến reference gốc ở bên gọi.

### 4.2. Có thể có `NullPointerException` trước khi callee bắt đầu

Với `obj.method()` và `obj == null`, một instance invocation không thể có receiver hợp lệ để bắt đầu method tương ứng; JVM có thể ném `NullPointerException` theo quy tắc opcode. **Không có frame callee chạy thân phương thức `method()` bình thường** rồi mới kiểm tra null.

Nếu lời gọi còn đối số là các biểu thức có side effects, cần hiểu thứ tự đánh giá của **Java source** và thời điểm kiểm tra null theo ngữ nghĩa gọi cụ thể. Không mặc định “không có gì bên trong biểu thức lời gọi được đánh giá” chỉ vì receiver null.

## 5. Năm bytecode dành cho method invocation

| Opcode | Dạng lời gọi chủ yếu | Có receiver `this` truyền trực tiếp? |
| --- | --- | --- |
| `invokestatic` | Static method của class/interface | **Không** |
| `invokevirtual` | Instance method với cơ chế lựa chọn mục tiêu phù hợp runtime class | **Có** |
| `invokeinterface` | Gọi interface method trên một object, với resolution/dispatch phù hợp | **Có** |
| `invokespecial` | Constructor `<init>`, private instance method, lời gọi `super`/một số lời gọi đặc biệt | **Có** |
| `invokedynamic` | Call site được liên kết qua bootstrap method; thường liên quan lambda, string concatenation, dynamic language support | **Không có receiver ngầm cố định**; tất cả argument types có trong call-site descriptor |

Đây là phân nhóm giúp đọc bytecode, không phải định nghĩa đầy đủ mọi trường hợp đặc biệt như signature-polymorphic methods hoặc thay đổi lựa chọn method qua version JVM.

### 5.1. `invokestatic`

```java
int answer = Math.abs(-7);
```

Nếu gọi static method, caller chuẩn bị các arguments trên operand stack, sau đó `invokestatic` thực hiện gọi. Callee nhận tham số đầu ở local 0. Không có `this` hay objectref cho class `Math`.

**Không phải** vì `Math.abs` không dùng object nên nó “không cần frame” theo mô hình JVM: lời gọi static Java method vẫn có ngữ nghĩa invocation; runtime/JIT có thể tối ưu hoặc dùng intrinsic.

### 5.2. `invokevirtual`

```java
String text = "hello";
int size = text.length();
```

Caller đặt objectref `text` lên operand stack rồi gọi method. JVM dựa vào loại đối tượng/phân giải method để chọn implementation theo quy tắc virtual dispatch. Ở callee, slot 0 là `this`.

Một lời gọi Java source có thể liên hệ với phương thức override; điều này không thay đổi vị trí receiver/arguments ở callee.

### 5.3. `invokeinterface`

```java
Runnable job = () -> System.out.println("run");
job.run();
```

Về ngữ nghĩa, `job.run()` gọi interface method trên receiver object. Operand stack có objectref `job` trước `invokeinterface`; receiver trở thành local 0 của method được chọn.

Tính trừu tượng interface không có nghĩa JVM không có đối tượng nhận lời gọi.

### 5.4. `invokespecial` và constructor

```java
Object item = new Object();
```

Bytecode điển hình ở caller:

```text
new java/lang/Object
dup
invokespecial java/lang/Object.<init>:()V
astore_1  // nếu compiler chọn local 1 cho item
```

**`new`** tạo reference đến object chưa khởi tạo theo ngữ nghĩa JVM; **`dup`** giữ một reference trên operand stack để dùng sau khi constructor hoàn tất. **`invokespecial <init>`** gọi instance initialization method có receiver, nhưng constructor có descriptor **trả `void`**, nên lệnh gọi constructor **không trả objectref như kết quả method**. Reference dùng cho `astore_1` là reference được giữ bằng `dup`.

Đoạn bytecode trên là **minh họa**; index lưu biến sẽ phụ thuộc chỗ đoạn code xuất hiện, và JVM verifier kiểm tra việc object reference chưa khởi tạo được sử dụng đúng quy tắc.

### 5.5. `invokedynamic` khác ở cách liên kết call site

`invokedynamic` được liên kết theo bootstrap method; call-site descriptor khai báo arguments và kiểu trả về. Không phải mọi dynamic call đều có một `this` ngầm ở slot 0 như instance invocation.

Nếu một argument là object reference, nó chỉ là **một đối số tường minh theo descriptor** của call site. Khi bootstrap dẫn tới thực thi Java methods khác, những phương thức đó vẫn tuân theo ngữ nghĩa frame và receiver phù hợp với kiểu gọi thực tế.

## 6. Kiểu trả về quyết định opcode và tác động lên caller stack

Java source `return expression;` được compiler chuyển thành lệnh return có loại phù hợp với descriptor.

| Kiểu trả về | Opcode JVM | Giá trị chuyển về caller |
| --- | --- | --- |
| `int` và các kiểu `boolean`, `byte`, `short`, `char` theo dạng phù hợp | `ireturn` | Một giá trị int-family, category 1 |
| `long` | `lreturn` | Một giá trị `long`, category 2 |
| `float` | `freturn` | Một giá trị `float`, category 1 |
| `double` | `dreturn` | Một giá trị `double`, category 2 |
| Reference type, array | `areturn` | Một object/array reference, category 1 |
| `void`, constructor `<init>` | `return` | **Không có giá trị trả về** |

Điều đáng nhớ là opcode `return` **không có chữ cái đứng trước** chỉ dùng khi method trả `void`. `ireturn` và `areturn` không phải hai cách tùy ý để trả cùng một loại; verifier và method descriptor ràng buộc kiểu phù hợp.

### 6.1. Trả về không đồng nghĩa ghi trực tiếp vào biến của caller

Giả sử:

```java
int result = add(2, 3);
```

Callee dùng `ireturn` để đưa `5` về **operand stack của caller**. Sau đó caller mới quyết định làm gì với kết quả:

```text
caller: iconst_2, iconst_3, invokestatic add:(II)I
                                    │
callee: iload_0, iload_1, iadd, ireturn
                                    │
                                    ▼
caller operand stack: [..., int 5]
caller: istore_0            // lưu vào biến local result
```

Nếu caller viết `System.out.println(add(2, 3))`, kết quả có thể được dùng trực tiếp để tạo arguments cho lệnh gọi tiếp theo mà **không cần `istore` vào một local variable**.

Đối với `long` hoặc `double`, giá trị trả về vẫn là **một giá trị category 2**, chiếm **hai đơn vị độ sâu** trên operand stack caller. Không có hai lệnh return cho hai nửa của nó.

### 6.2. Sau khi return, frame nào hiện hành?

Khi một phương thức hoàn tất bình thường:

1. Method trả giá trị (nếu có) bằng typed return opcode.
2. Frame callee bị loại khỏi ngữ cảnh frame hiện hành theo mô hình JVM.
3. Frame caller trở lại là frame hiện hành.
4. JVM khôi phục luồng thực thi ở vị trí **tiếp sau invocation instruction** của caller.
5. Giá trị trả về (nếu có) trở thành một giá trị trên operand stack của caller.

Đó là lý do caller có thể tiếp tục tính toán với kết quả của callee mà không cần truy cập trực tiếp local variables riêng của callee.

Các chi tiết runtime như machine registers, return address vật lý hoặc optimizations không làm thay đổi kết quả ngữ nghĩa này.

### 6.3. `void` và trường hợp ngoại lệ

Với phương thức:

```java
static void log(String message) {
    System.out.println(message);
}
```

Khi `log` kết thúc bằng `return`, caller **không được push bất kỳ giá trị method return nào**. Thân method có thể đã tạo side effects (in stdout), nhưng không có kết quả biểu thức để sử dụng.

Ngược lại, nếu callee ném exception và không xử lý ở trong nó, phương thức kết thúc **abruptly**, không có giá trị return bình thường. JVM tìm exception handler phù hợp, có thể unwind qua các frames cho tới khi gặp handler thích hợp hoặc thread kết thúc.

**Không được nói “exception được trả về như một object ở operand stack qua `areturn`”.** Exception propagation là cơ chế điều khiển riêng; khi handler được chọn, JVM thiết lập trạng thái operand stack cho handler theo đặc tả.

## 7. Ví dụ Java 17 chạy được: static call, instance call, `this` và long return

Tạo file **`FrameCallDemo.java`**:

```java
public class FrameCallDemo {
    private final int factor;

    public FrameCallDemo(int factor) {
        this.factor = factor;
    }

    static long mix(long base, double ratio, int extra) {
        return base + (long) (ratio * extra);
    }

    long adjust(long base, double ratio, int extra) {
        return mix(base, ratio, extra) + factor;
    }

    static void display(long value) {
        System.out.println("display=" + value);
    }

    public static void main(String[] args) {
        FrameCallDemo demo = new FrameCallDemo(3);
        long result = demo.adjust(10L, 2.5, 4);

        System.out.println("result=" + result);
        display(result);
    }
}
```

Biên dịch/chạy trên JDK 17+:

```bash
javac --release 17 FrameCallDemo.java
java FrameCallDemo
```

**Stdout chính xác:**

```text
result=23
display=23
```

Tại `mix`: `(long) (2.5 * 4) = (long) 10.0 = 10L` nên `base + 10 = 20L`. `adjust` cộng thêm field `factor = 3` và trả `23L`. `display` chỉ in kết quả, không trả value.

### 7.1. Sơ đồ lời gọi trong chương trình

```text
main(String[] args)
  local 0 = args
  local 1 = demo (tham chiếu, trong bytecode thông thường)
  local 2–3 = result (long)

  new FrameCallDemo
    └─ invokespecial <init>(I)V
         local 0 = this (demo)
         local 1 = factor = 3
         return (void)

  gọi demo.adjust(10L, 2.5, 4)
    caller operand stack:
      [..., demo ref, long 10, double 2.5, int 4]
    invokevirtual adjust:(JDI)J
         │
         ▼
         frame adjust:
           local 0 = this
           local 1–2 = base (long)
           local 3–4 = ratio (double)
           local 5   = extra (int)
           │
           └─ invokestatic mix:(JDI)J
                 frame mix:
                   local 0–1 = base
                   local 2–3 = ratio
                   local 4   = extra
                   lreturn 20L
           │
           <long 20L> trở về operand stack của adjust
           đọc this.factor và cộng → lreturn 23L
         │
         ▼
    main nhận long 23L trên operand stack
    lstore ...  → result = 23L

  gọi static display(result)
    invokestatic display:(J)V
    callee local 0–1 = result (long)
    return (void), không có giá trị
```

Sơ đồ local của `main` là **một cách cấp phát thường thấy** từ `javac` cho mã này; compiler khác có thể tái sử dụng index khác. Local index cho **receiver và tham số tại đầu `adjust`, `mix`, `display`** lại được quy định bởi thứ tự/type của method descriptor, bất kể có bao nhiêu temporaries ở thân method.

### 7.2. Tại thời điểm `invokevirtual adjust`, stack caller chứa gì?

Method descriptor: **`(JDI)J`**.

| Thứ tự từ đáy lên đỉnh của các giá trị cho lời gọi | Kiểu | Độ sâu operand stack tăng |
| --- | --- | ---: |
| Object reference `demo` | Reference | 1 |
| `10L` | Long (category 2) | 2 |
| `2.5` | Double (category 2) | 2 |
| `4` | Int | 1 |
| **Tổng** | Receiver + ba đối số | **6 đơn vị** |

Caller phải có đủ operand stack capacity tại điểm này. Lệnh gọi tiêu thụ các giá trị trên. Frame `adjust` có receiver ở slot 0, các giá trị tham số ở slot 1–5. Khi `adjust` kết thúc bằng `lreturn`, caller nhận **một giá trị long** có độ sâu 2 trên operand stack.

**Lưu ý:** con số 6 là độ sâu **của chính các giá trị dùng cho lời gọi**. `max_stack` thực tế của cả phương thức `main` có thể cao hơn tùy những phép tính và instruction khác.

### 7.3. Quan sát bytecode bằng `javap`

```bash
javap -c -p FrameCallDemo
javap -v -p FrameCallDemo
```

Các vùng cần tìm:

- `FrameCallDemo(int)`: `aload_0` lấy `this`, `iload_1` lấy tham số `factor`, `putfield` ghi field, cuối cùng `return`.
- `static long mix(long, double, int)`: `lload_0` lấy `base`, `dload_2` lấy `ratio`, `iload 4` lấy `extra`; các lệnh nhân/chuyển kiểu/cộng sẽ dẫn tới `lreturn`.
- `long adjust(long, double, int)`: `lload_1`, `dload_3`, `iload 5` chuẩn bị arguments cho `invokestatic mix`. Sau đó lấy `this.factor` và dùng `lreturn`.
- `static void display(long)`: nhận `long` ở local slots 0–1; cuối phương thức có `return`.
- `main`: `new`, `dup`, `invokespecial <init>` khi tạo instance; `invokevirtual adjust` với descriptor `(JDI)J`; `lstore` lưu kết quả và `invokestatic display` khi in.

Các lệnh exact offsets có thể khác giữa các compiler, cấu hình compiler và version JDK. Bảng chỉ nêu **những quan hệ bytecode cốt lõi** cần đối chiếu, không giả định mọi compiler đặt cùng instruction index.

### 7.4. `max_locals` không phải số tham số nguồn

Với `adjust(long, double, int)`, ngay khi vào method:

```text
Slot 0     : this
Slot 1,2   : long base
Slot 3,4   : double ratio
Slot 5     : int extra
Cần ít nhất 6 local slots
```

Với `mix(long, double, int)`:

```text
Slot 0,1   : long base
Slot 2,3   : double ratio
Slot 4     : int extra
Cần ít nhất 5 local slots
```

Không có cặp `long` hoặc `double` nào bắt buộc phải bắt đầu ở index chẵn. `adjust` có `long` ở `1–2` và `double` ở `3–4` hoàn toàn hợp lệ.

## 8. Java truyền tham số theo giá trị, kể cả reference

Một hiểu nhầm xuất hiện khi nghe “truyền đối số object”: Java **luôn truyền các giá trị** của primitive hoặc reference. Caller đặt một **bản sao giá trị** vào lời gọi; callee nhận giá trị ấy trong local variable của frame riêng.

Với primitive:

```java
static void change(int value) {
    value = 99;
}
```

Gán lại `value` của callee không làm biến `int` của caller đổi theo.

Với reference:

```java
static void mutateBox(Box box) {
    box.value = 99; // Thay đổi trạng thái object được chia sẻ qua reference.
}

static void replaceBox(Box box) {
    box = new Box(); // Chỉ gán lại local reference của callee.
}
```

Đoạn trên là **minh họa** và yêu cầu khai báo `Box` khi đưa vào chương trình. Ví dụ độc lập có ngay dưới đây.

### 8.1. Ví dụ chạy được: thay object khác với gán lại reference

Tạo file **`ReferenceValueDemo.java`**:

```java
public class ReferenceValueDemo {
    static class Box {
        int value;

        Box(int value) {
            this.value = value;
        }
    }

    static void mutate(Box box) {
        box.value = 99;
    }

    static void replace(Box box) {
        box = new Box(123);
        System.out.println("inside replace=" + box.value);
    }

    public static void main(String[] args) {
        Box shared = new Box(10);

        mutate(shared);
        System.out.println("after mutate=" + shared.value);

        replace(shared);
        System.out.println("after replace=" + shared.value);
    }
}
```

Chạy:

```bash
javac ReferenceValueDemo.java
java ReferenceValueDemo
```

Stdout:

```text
after mutate=99
inside replace=123
after replace=99
```

Lý do:

- `shared` trong `main` chứa một **reference value** tới object `Box`.
- Khi gọi `mutate(shared)`, reference value được sao chép thành parameter `box` tại **local slot 0** của static callee. Cả hai references cùng chỉ một object, nên đổi field thấy từ caller.
- Khi gọi `replace(shared)`, phép `box = new Box(123)` chỉ đổi **local slot của callee** sang object mới; `shared` ở frame caller vẫn chỉ tới object cũ có `value = 99`.
- Không có phép “truyền biến `shared` theo tham chiếu” để callee trực tiếp thay ô local của caller.

Điều này cũng áp dụng khi truyền receiver `this`: reference đến object được dùng để chọn/khởi chạy instance method, chứ bản thân object không bị sao chép sang frame mới.

## 9. Đánh giá đối số, boxing và varargs trước method invocation

Mã Java có thể trông như chỉ “đưa một biến” vào lời gọi, nhưng compiler có thể phát sinh những phép toán chuẩn bị giá trị.

### 9.1. Thứ tự đánh giá arguments

```java
static int first() {
    System.out.print("A");
    return 1;
}

static int second() {
    System.out.print("B");
    return 2;
}

static int add(int a, int b) {
    return a + b;
}
```

Khi caller gọi `add(first(), second())`, Java đánh giá các biểu thức đối số theo thứ tự phù hợp với ngôn ngữ (với trường hợp đơn giản trên: `first()` rồi `second()`), chuẩn bị chúng cho `invokestatic add`. Side effects in `A` trước `B`. LIFO stack **không** có nghĩa `second()` thực thi trước `first()`.

### 9.2. Boxing và unboxing

Với lời gọi `accept(Integer.valueOf(7))` hoặc `accept(7)` khi chữ ký nhận `Integer`, compiler có thể phát sinh boxing method call trước method invocation chính.

Với parameter primitive `int` mà caller có `Integer`, compiler có thể gọi `intValue()` để unbox, và có thể ném `NullPointerException` nếu reference null. Những lời gọi/chuyển đổi này là công việc chuẩn bị arguments; callee cuối cùng vẫn nhận **kiểu đã được khai báo trong descriptor**.

### 9.3. Varargs thực chất là array parameter

```java
static int count(String... items) {
    return items.length;
}
```

Chữ ký runtime có parameter là `String[]` (descriptor `([Ljava/lang/String;)I`). Với `count("a", "b")`, compiler thường tạo một `String[]` gồm hai phần tử rồi truyền **một array reference** vào callee. Không có opcode đặc biệt tên `invokevarargs` và không có hai local parameters tách rời cho `"a"`, `"b"`.

Tuy nhiên Java source có thể truyền trực tiếp một mảng phù hợp trong một varargs invocation. Khi kiểm tra bytecode, đừng suy số lượng slot chỉ từ số phần tử varargs ở vị trí gọi.

## 10. Method descriptor: bản đồ loại đối số và loại trả về

Method descriptor của JVM ghi **loại và thứ tự** parameters, rồi loại return. Ví dụ:

```text
(I)I                         int → int
(II)I                        (int,int) → int
(JDI)J                       (long,double,int) → long
(Ljava/lang/String;)V        (String) → void
([Ljava/lang/String;)V       (String[]) → void
()D                          () → double
()Ljava/lang/Object;         () → Object
```

Quy tắc nhận diện:

| Ký tự descriptor | Kiểu |
| --- | --- |
| `B` | `byte` |
| `C` | `char` |
| `D` | `double` |
| `F` | `float` |
| `I` | `int` |
| `J` | `long` |
| `S` | `short` |
| `Z` | `boolean` |
| `V` | `void` (chỉ cho return type) |
| `Lpkg/Type;` | Class/interface reference |
| `[` theo sau một kiểu | Một chiều array; nhiều `[` cho nhiều chiều |

Descriptor **không ghi `this`** cho instance methods. Điều này thường gây nhầm lẫn khi so sánh với local slots:

```text
instance method: adjust:(JDI)J

descriptor parameter types: 3
receiver objectref:        1 (ngầm khi gọi instance)
parameter local slots:     2 + 2 + 1 = 5
total local slots đầu vào: 1 + 5 = 6

static method: mix:(JDI)J

descriptor parameter types: 3
receiver objectref:        0
parameter local slots:     2 + 2 + 1 = 5
total local slots đầu vào: 5
```

**Không nên lấy số dấu phẩy hay độ dài chuỗi descriptor** để suy `max_locals`. Với reference `Ljava/lang/String;` dù chuỗi descriptor dài, nó vẫn là **một reference category 1** ở JVM.

## 11. Sự khác biệt giữa hoàn tất bình thường và exception propagation

Giả sử:

```java
static int parse(String text) {
    return Integer.parseInt(text);
}
```

Nếu `text` hợp lệ, callee trả một `int`, bên gọi tiếp tục và có thể dùng kết quả.

Nếu `Integer.parseInt` ném `NumberFormatException`:

1. Lời gọi sâu hơn không hoàn tất bình thường.
2. JVM tìm handler `catch` phù hợp trong các frames theo quy tắc exception dispatch.
3. Nếu không có handler trong một frame, frame đó bị unwound, các ngữ cảnh gọi được rời bỏ.
4. Tại handler được chọn, exception object được đưa vào trạng thái operand stack phù hợp để handler sử dụng (thường là phần tử duy nhất của stack tại điểm handler).
5. Không có typed return value bình thường cho lời gọi `parse` nếu exception chưa được bắt và xử lý để quay lại đường thực thi thường.

Một phương thức có thể dùng `try/finally` hoặc `try-with-resources`; Java compiler phải đảm bảo side effects phù hợp trong quá trình return/exception. Điều đó có thể bổ sung handler/branch bytecode, nhưng không biến exception thành `ireturn` hay `areturn`.

### 11.1. Điều gì xảy ra khi callee là native method?

Với `native` method, JVM có thể chuyển sang code native thông qua cơ chế triển khai phụ thuộc platform (ví dụ JNI), nên cách passing arguments ở mức ABI/native stack có thể khác. Tuy nhiên từ góc nhìn bytecode Java caller, lời gọi vẫn tuân theo descriptor, objectref/argument values và kiểu kết quả mà JVM quy định.

Do đó không được suy “nếu Java method nhận `long` ở hai slots thì C function JNI nhận đúng hai thanh ghi 32-bit” mà không hiểu ABI riêng.

## 12. Frame recursion, stack overflow và vòng đời dữ liệu

Nếu phương thức tự gọi lại, **mỗi invocation có frame riêng**:

```java
static int factorial(int n) {
    if (n <= 1) return 1;
    return n * factorial(n - 1);
}
```

Với `factorial(3)`, các frames logic tồn tại trong lúc gọi sâu:

```text
factorial(3) ─ đang đợi factorial(2)
    factorial(2) ─ đang đợi factorial(1)
        factorial(1) ─ ireturn 1
    factorial(2) ─ nhân 2 * 1, ireturn 2
factorial(3) ─ nhân 3 * 2, ireturn 6
```

Mỗi callee có local slot `0` chứa `n` **riêng của lời gọi đó**. Caller đang đợi không bị overwrite local `n` khi một frame mới cũng dùng slot 0.

Đệ quy sâu có thể dẫn tới `StackOverflowError` khi JVM stack không đủ chỗ cho những frame cần thiết. Nó khác với `OutOfMemoryError: Java heap space`. Tăng heap `-Xmx` không phải giải pháp mặc định cho stack overflow; cần xem thuật toán, recursion depth và giới hạn stack phù hợp.

**Đời sống dữ liệu:** local primitive hoặc reference trong frame không phải mãi tồn tại sau khi method kết thúc. Nếu local giữ reference tới object được caller/trường static/container khác giữ lại, object có thể vẫn reachable trên heap sau return. Frame hết hoạt động không chứng minh GC sẽ thu hồi object ngay.

## 13. JVM có thực sự tạo frame vật lý cho mọi lời gọi?

JVMS mô tả **ngữ nghĩa** dưới dạng frames và operand stacks. Một JVM tối ưu như HotSpot có thể:

- **Inlining:** thay lời gọi bằng mã của callee trong compiled caller, trong khi vẫn giữ hành vi Java.
- **Scalar replacement:** loại bỏ allocation không cần thiết hoặc biểu diễn dữ liệu ở các thanh ghi/giá trị nội bộ.
- **Register allocation:** đặt arguments/temporaries trong CPU registers thay vì layout y hệt biểu đồ local slots.
- **Deoptimization:** phục hồi trạng thái Java-level phù hợp khi những giả định tối ưu không còn đúng.
- **Native/intrinsic calls:** thực hiện một số thao tác theo cơ chế đặc thù, không hiện ra như một chuỗi bytecode thông dịch đầy đủ ở runtime.

Không được suy rằng “method nhỏ có `invokestatic` chắc chắn làm chậm do phải tạo OS thread hoặc native frame mới”. Gọi một phương thức bình thường **không tạo thread mới**, và JIT có thể tối ưu chi phí invocation.

Khi phân tích kết quả profiler/thread dump, một số frame logic có thể được tái dựng hoặc biểu diễn theo quy ước của công cụ; đừng đồng nhất mọi khung logic Java với một vùng cố định trong machine stack.

## 14. Thực hành xem `max_locals`, `max_stack` và call stack

### 14.1. Kiểm tra `FrameCallDemo` với `javap`

```bash
javac --release 17 FrameCallDemo.java
javap -c -p FrameCallDemo
javap -v -p FrameCallDemo
```

Tìm phần phương thức `adjust`. Đặc biệt xem:

1. **Method descriptor `(JDI)J`.** Nó mô tả ba tham số và kết quả `long`.
2. **Không có `this` trong descriptor**, nhưng lệnh `aload_0` trong instance method tham chiếu receiver.
3. **`lload_1` và `dload_3`:** cho thấy `long`/`double` bắt đầu ở slots 1/3.
4. **`invokestatic`:** gọi `mix` không có objectref.
5. **`lreturn`:** trả `long` bằng typed return instruction.
6. **`Code: stack=..., locals=..., args_size=...`:** chỉ số do `javap` in để giúp hiểu, nhưng nhớ `max_locals` và stack depth là khái niệm khác nhau.

Dùng `javac -g` nếu cần thông tin debug như `LocalVariableTable`. Không có debug local-variable names thì bạn vẫn đọc được index qua lệnh bytecode và method descriptor.

### 14.2. Hiểu ví dụ stack trace

Khi Java exception được in, thường thấy:

```text
Exception in thread "main" java.lang.IllegalArgumentException: invalid
    at Example.callee(Example.java:20)
    at Example.caller(Example.java:12)
    at Example.main(Example.java:5)
```

Đây là **mẫu minh họa**, không phải output của các chương trình ở trên. Stack trace thể hiện chuỗi lời gọi Java gần thời điểm exception: callee ở trên, caller ở dưới. Nó không in nguyên local-variable array hay operand stack theo chỉ số.

Muốn xem chi tiết method signature và instruction, dùng `javap`; muốn quan sát các lời gọi/runtime frames, dùng debugger/profiler/thread dump theo phạm vi thích hợp. Tránh dự đoán chính xác physical frame layout từ stack trace.

### 14.3. Phân biệt phương thức và thread

Một lời gọi `demo.adjust(...)` **thêm frame logic** trên JVM stack của thread hiện tại; nó không tự tạo thread mới. Chỉ khi chương trình sử dụng Thread/Executor/virtual thread mechanism phù hợp thì có một ngữ cảnh thread mới cho task. Trong mỗi thread, nhiều frame method có thể xuất hiện trong call chain; các thread không chia sẻ local slots của nhau.

## 15. Các nhầm lẫn thường gặp

1. **“Method arguments được truyền trực tiếp vào local slots của caller.”** Sai. Caller chuẩn bị arguments trên **operand stack caller**; callee nhận chúng trong **local-variable array riêng**.
2. **“Instance method có tham số đầu ở slot 0.”** Sai. Slot 0 là `this`; tham số đầu bắt đầu ở 1.
3. **“Static method vẫn có `this` ở slot 0 nhưng không dùng.”** Sai. Static method không nhận receiver, tham số đầu bắt đầu ở 0.
4. **“Có ba tham số thì cần đúng ba slots.”** Sai khi có `long` hoặc `double` chiếm hai slots, và instance receiver thêm một slot.
5. **“Constructor trả object bằng `areturn`.”** Sai. `<init>` có return descriptor `V`; reference object để gán được giữ từ `new`/`dup` ở caller.
6. **“`ireturn` lưu kết quả thẳng vào biến Java `result`.”** Sai. Nó trả về operand stack caller; caller có thể `istore` sau đó, hoặc dùng trực tiếp.
7. **“`return` trả một giá trị null.”** Sai. `return` dành cho method `void`, không push kết quả nào.
8. **“Java truyền object theo tham chiếu nên callee đổi được biến caller.”** Sai. Java truyền **giá trị reference**; gán lại local parameter không thay local variable caller.
9. **“Object được copy mỗi khi dùng `this`.”** Sai. Reference được truyền, nhiều frames có thể tham chiếu cùng object heap.
10. **“`invokedynamic` luôn có `this` như `invokevirtual`.”** Sai. Argument list đến từ call-site descriptor, không có receiver ngầm bất biến.
11. **“Phương thức `throw` exception vẫn sử dụng `areturn`.”** Sai. Exception propagation là abrupt completion, không có typed normal return.
12. **“Mỗi lời gọi bytecode luôn tạo thêm native stack frame vật lý ở HotSpot.”** Không phải cam kết. JIT có thể inline và tối ưu layout nhưng phải giữ ngữ nghĩa Java.
13. **“`max_stack` là số lượng method invocation đang mở.”** Sai. Nó là độ sâu tối đa operand stack của **một** frame, không phải độ sâu call stack.
14. **“Frame của hai thread dùng chung local slot 0.”** Sai. Chỉ số có nghĩa trong từng frame cụ thể, không phải tên biến chia sẻ toàn JVM.
15. **“`lreturn` trả hai giá trị `int`.”** Sai. Nó trả **một giá trị `long` category 2**.

## 16. Tự kiểm tra (có đáp án)

**Câu 1.** Method `static double avg(long sum, int count)` có parameter slots nào?

**Đáp án:** `sum` ở slots **0–1**, `count` ở slot **2**. Method cần ít nhất **3 slots** cho tham số, descriptor `(JI)D`.

**Câu 2.** Nếu `avg(long,int)` trở thành instance method, các index thay đổi thế nào?

**Đáp án:** Slot **0** là `this`, `sum` ở **1–2**, `count` ở **3**. Descriptor **vẫn `(JI)D`**, vì receiver không ghi trong descriptor.

**Câu 3.** Một instance method nhận `long, double, int`, caller cần chuẩn bị những gì trên operand stack ngay trước `invokevirtual`?

**Đáp án:** Từ đáy lên đỉnh: **objectref**, **long**, **double**, **int**, theo descriptor. Tổng độ sâu dành cho chúng là **1+2+2+1 = 6** đơn vị.

**Câu 4.** Method `static long parse(...)` kết thúc bằng `lreturn`. Giá trị đi đâu?

**Đáp án:** Một `long` category 2 được đưa về **operand stack của caller**; caller có thể dùng tiếp hoặc `lstore` vào local variable riêng.

**Câu 5.** Tại sao `new SomeType()` không có nghĩa constructor `<init>` trả về reference bằng `areturn`?

**Đáp án:** Constructor descriptor trả **`void`**. Bytecode `new` tạo uninitialized object reference; caller có thể giữ một bản bằng `dup` trong khi `invokespecial <init>` thực hiện initialization, rồi dùng reference đã giữ.

**Câu 6.** Vì sao `replace(Box box)` gán `box = new Box()` không thay giá trị biến `shared` của caller?

**Đáp án:** `box` là local parameter trong frame callee, chứa bản sao của **giá trị reference**. Gán lại nó không ghi vào local slot chứa `shared` trong frame caller.

**Câu 7.** Nếu `callee` ném exception chưa được bắt, caller có nhận normal return trên operand stack không?

**Đáp án:** Không. JVM xử lý **abrupt completion** và stack unwinding; không có normal return value nếu đường exception không được xử lý để đi tiếp theo một lời gọi thành công.

**Câu 8.** Vì sao một phương thức Java rất nhỏ có nhiều lần gọi không nhất thiết tạo chi phí frame vật lý tương ứng?

**Đáp án:** JIT có thể inline/tối ưu lời gọi, dùng thanh ghi và những biểu diễn nội bộ khác; JVM chỉ phải bảo toàn **ngữ nghĩa** invocation và return theo Java/JVMS.

## 17. Tổng hợp bằng một sơ đồ

```text
                 Frame của caller (current)
                 ┌───────────────────────────────┐
                 │ Local variables: dữ liệu riêng│
                 │ Operand stack:               │
                 │ [..., receiver?, arg1, arg2] │
                 └───────────────┬───────────────┘
                                 │ invoke*
                                 │ lấy receiver/args đúng descriptor
                                 ▼
                 Frame của callee (new current)
                 ┌───────────────────────────────┐
                 │ Instance: slot 0 = this        │
                 │ Static:   slot 0 = param đầu  │
                 │ long/double = hai local slots │
                 │ Operand stack: ban đầu rỗng   │
                 │ Thực thi lệnh của callee      │
                 └───────────────┬───────────────┘
                                 │
                     ┌───────────┴───────────┐
                     │                       │
                  *return                 athrow /
               normal completion          exception
                     │                       │
                     ▼                       ▼
        Kết quả → caller operand       Tìm handler /
        stack (nếu non-void)           unwind frames
                     │
                     ▼
            Caller tiếp tục sau invoke
```

**Điểm cốt lõi:** Caller chuẩn bị đối số **trên operand stack của chính mình**; callee nhận giá trị đó trong **local-variable array của frame mới**. **`this` chiếm local slot 0 ở instance methods**, còn static methods bắt đầu tham số ở slot 0. Typed return opcodes đưa **một giá trị có kiểu** về operand stack caller; `void` và exception không có normal return value. Mô hình này giúp đọc bytecode, descriptor, frame, đệ quy và stack trace đúng mà không đánh đồng các khái niệm với stack native hay vị trí bộ nhớ vật lý.

### Tài liệu tham khảo chuẩn

- [JVMS Java SE 17 — Frames (`2.6`)](https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-2.html#jvms-2.6).
- [JVMS Java SE 17 — Local Variables (`2.6.1`)](https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-2.html#jvms-2.6.1): quy tắc `this`, static parameters và category-2 slots.
- [JVMS Java SE 17 — Operand Stacks (`2.6.2`)](https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-2.html#jvms-2.6.2): argument preparation và method results.
- [JVMS Java SE 17 — Normal/Abrupt Method Invocation Completion (`2.6.4–2.6.5`)](https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-2.html#jvms-2.6.4).
- [JVMS Java SE 17 — Method Descriptors (`4.3.3`)](https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-4.html#jvms-4.3.3).
- [JVMS Java SE 17 — Method invocation opcodes (`6.5`)](https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-6.html).
- [JVMS Java SE 17 — Compiling examples: Passing/Receiving Arguments (`3.5–3.6`)](https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-3.html#jvms-3.6).
- [JDK 17 `javap` manual](https://docs.oracle.com/en/java/javase/17/docs/specs/man/javap.html).


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="stack-frames-and-calls-003">invokestatic, invokespecial, invokevirtual và invokeinterface</a>

<details>
<summary>Xem chi tiết</summary>

# `invokestatic`, `invokespecial`, `invokevirtual` và `invokeinterface` trong JVM

## 1. Bốn lệnh gọi phương thức giải quyết những vấn đề khác nhau

Mã nguồn Java có thể dùng cùng một cú pháp `object.method(...)` hoặc `Type.method(...)`, nhưng ở mức JVM, trình biên dịch phải chọn một **instruction gọi phương thức** phù hợp với tính chất method và cách liên kết. Bốn lệnh cần phân biệt là:

| Instruction | Vai trò chính | Có receiver object trên operand stack? | Cách chọn method cần thực thi |
| --- | --- | --- | --- |
| `invokestatic` | Gọi **static method** của class hoặc interface | **Không** | Dựa trên symbolic reference tới static method đã được resolve; không dispatch theo kiểu runtime của object |
| `invokespecial` | Gọi instance initialization method `<init>`, lời gọi `super` và những lời gọi instance có quy tắc đặc biệt | **Có** | Áp dụng special lookup/selection của JVMS; không phải dispatch override thông thường của `invokevirtual` |
| `invokevirtual` | Gọi instance method thông thường qua **class type** | **Có** | Chọn implementation theo class runtime của receiver và quy tắc override |
| `invokeinterface` | Gọi instance method qua **interface type** | **Có** | Chọn method phù hợp mà runtime class của receiver triển khai hoặc kế thừa, gồm quy tắc default methods |

Đặc tả còn có **`invokedynamic`** cho call site được liên kết thông qua bootstrap method. Nó là instruction gọi thứ năm, nhưng có mô hình liên kết khác và không phải đối tượng chính của bài. Trong Java 17, nối chuỗi có thể sinh `invokedynamic`, nên đừng nghĩ mọi dòng output của `javap -c` chỉ chứa bốn instruction nêu trên.

**Tư duy đúng:** kiểu khai báo của biểu thức và cách method được gọi giúp compiler tạo instruction cùng constant-pool reference; sau đó JVM resolve và (với những lệnh cần dynamic dispatch) chọn implementation dựa trên đối tượng thực. “Tên method giống nhau” chưa đủ để kết luận opcode sẽ giống nhau.

## 2. Bối cảnh bắt buộc: frame, operand stack và method descriptor

Mỗi lời gọi method Java có thể tạo một **frame** trong JVM stack của thread. Frame chứa local variables, operand stack và thông tin hỗ trợ liên kết/trả về. Trước instruction `invoke*`, JVM sử dụng các operand đã được chuẩn bị trên **operand stack** của caller.

Ví dụ chữ ký JVM:

| Java declaration | Method descriptor trong class file |
| --- | --- |
| `static int twice(int n)` | `(I)I` |
| `String say()` | `()Ljava/lang/String;` |
| `void save(String value, long time)` | `(Ljava/lang/String;J)V` |
| `Child()` | `<init>:()V` |
| `void process(int[] values)` | `([I)V` |

Descriptor ghi **kiểu của các tham số và kiểu trả về**, không mang tên tham số Java như `n` hay `time`. JVM dùng symbolic method reference gồm **loại sở hữu method, tên method và descriptor** (cùng thông tin constant pool khác) để tìm method phù hợp. Method overload dựa trên các chữ ký khác nhau sẽ có descriptor khác nhau tại mức JVM.

### Sơ đồ một lời gọi instance

~~~text
Caller frame:
  local variables:  ...
  operand stack:    [..., objectref, arg1, arg2]
                                      |
                        invokevirtual / invokeinterface /
                        invokespecial
                                      v
Callee frame (với instance method):
  local slot 0: this = objectref
  các slot kế tiếp: arg1, arg2, ...
  operand stack riêng của callee
                                      |
                           thực thi bytecode của callee
                                      |
                          xreturn / return / athrow
                                      v
Caller tiếp tục sau instruction invoke;
nếu có return value, nó được đưa vào operand stack của caller
~~~

Với `invokestatic`, **không có `this`**: tham số đầu tiên (nếu có) bắt đầu ở local slot 0 của callee. Với instance method, slot 0 thường chứa `this`. Giá trị `long` và `double` sử dụng **hai slot** local-variable theo mô hình frame JVM; số lượng tham số ở source không luôn bằng số local slots.

Ví dụ gọi `Child.say()` trả String: receiver là một reference, descriptor `()Ljava/lang/String;`, kết quả trở lại caller bằng `areturn`. Nếu callee ném exception, call không trả về bình thường; các frame có thể được tháo trong quá trình tìm exception handler. Gọi method không phải cứ “copy toàn bộ object vào frame mới”; frame nhận **giá trị reference** cho `this`.

## 3. Lớp, interface và thông tin symbolic reference trong constant pool

Instruction `invoke*` chỉ ra **constant-pool index** cần dùng cho method reference. Tệp class không ghi trực tiếp địa chỉ byte máy của một method vào từng chỗ gọi. Thông tin dạng:

~~~text
#18 = Methodref             #... // InvokeModesDemo.twice:(I)I
#45 = Methodref             #... // InvokeModesDemo$Parent.label:()Ljava/lang/String;
#49 = InterfaceMethodref    #... // InvokeModesDemo$Speaker.say:()Ljava/lang/String;
~~~

Đây là ví dụ cách `javap -v` trình bày, các số constant-pool index **có thể khác** theo compiler và binary.

Các điểm cần phân biệt:

- `CONSTANT_Methodref` và `CONSTANT_InterfaceMethodref` là các loại entry khác nhau; chúng chứa quan hệ tới class/interface và `NameAndType` tương ứng.
- Theo ràng buộc class-file version Java 17, `invokevirtual` dùng `CONSTANT_Methodref`; `invokeinterface` dùng `CONSTANT_InterfaceMethodref`. `invokestatic` và `invokespecial` có thể dùng `Methodref` hoặc `InterfaceMethodref` khi hợp lệ theo phiên bản class file và loại method.
- **Resolution** tìm symbolic method reference phù hợp, kiểm tra điều kiện liên kết/truy cập; **selection/dispatch** có thể còn phụ thuộc class thực của receiver ở `invokevirtual`/`invokeinterface`. Không gộp resolution và dispatch thành một ý “JVM đã biết method tại compile time”.
- Trình biên dịch Java kiểm tra kiểu, overload và khả năng truy cập tại compile time, nhưng runtime class và thư viện thực có thể khác phiên bản được dùng khi biên dịch, nên JVM vẫn có các **linkage errors** khi chạy.
- JIT/HotSpot có thể dùng vtable, itable, inline cache hoặc devirtualization để tối ưu. **Cơ chế dữ liệu cụ thể đó không phải định nghĩa bắt buộc của opcode trong JVM Specification**.

Một lệnh gọi có thể được tối ưu rất mạnh trong native code, nhưng ngữ nghĩa quan sát được vẫn phải phù hợp với cơ chế chọn method mà Java/JVM định nghĩa (trừ các trường hợp lỗi, hành vi không xác định bởi chương trình, hoặc khác biệt được đặc tả).

## 4. `invokestatic`: gọi static method không cần receiver

Khi Java gọi:

~~~java
int result = Math.max(4, 7);
~~~

Compiler Java thông thường phát sinh `invokestatic` tham chiếu static method `Math.max:(II)I`. Trước khi gọi, các đối số `4`, `7` được đưa lên operand stack. **Không có object receiver** cho method static, và bên callee không có `this`.

Về mặt khái niệm:

~~~text
iconst_4
bipush 7
invokestatic java/lang/Math.max:(II)I
istore_1
~~~

Đây là trình bày giản lược, không phải toàn bộ output `javap` được bảo đảm; biến và offset thực do compiler quyết định.

### Static hiding không phải override động

~~~java
class Parent {
    static String kind() { return "PARENT"; }
}
class Child extends Parent {
    static String kind() { return "CHILD"; }
}
~~~

`Parent.kind()` và `Child.kind()` là **hai lời gọi static riêng**. Method static trong class con có thể **hide** static method cùng chữ ký của class cha, nhưng không override nó như instance method.

Nếu code Java cho phép gọi static method qua một biểu thức receiver như `ref.kind()`, cách giải quyết method vẫn theo **kiểu tại compile time**, không phải dispatch theo object thực. Cách viết `Type.kind()` rõ ràng hơn và tránh khiến người đọc tưởng đây là virtual call.

**Class/interface initialization:** việc thực thi `invokestatic` có thể kích hoạt khởi tạo class hoặc interface **khai báo method static đã được resolve**, theo quy tắc initialization của JVM. Điều này tách biệt với việc tạo một object: static method có thể được gọi ngay cả khi chương trình không có instance nào của class. Không phát biểu quá đơn giản rằng “chỉ cần nhìn thấy tên class trong source là static initializer chắc chắn chạy”; điều kiện initialization do đặc tả quy định.

`invokestatic` cũng gọi được **static method của interface**, như `Speaker.version()` ở ví dụ bên dưới; vì vậy mô tả “chỉ gọi static method trong class” là không đủ cho Java hiện đại.

## 5. `invokespecial`: constructor, `super` và xử lý instance đặc biệt

`invokespecial` có semantics riêng so với virtual dispatch thông thường. Những trường hợp tiêu biểu:

1. **Constructor `<init>`**: khi bytecode tạo object bằng `new`, quá trình initialization instance sử dụng `invokespecial` để gọi constructor. Constructor của class con cũng gọi constructor của class cha theo quy tắc Java/JVM.
2. **Gọi method của superclass bằng `super.method(...)`**: mong muốn của source là **bắt đầu truy cập implementation ở phía cha** theo ngữ nghĩa `super`, chứ không chọn override của class con như `this.method()` thông thường.
3. **Gọi default method được chỉ định bằng `InterfaceName.super.method(...)`** khi Java và quan hệ superinterface cho phép.
4. **Một số lời gọi instance có quyền truy cập/quy tắc đặc biệt**, gồm những trường hợp liên quan private method. Không nên khẳng định rằng **mọi** private call ở **mọi** phiên bản compiler đều phải mang opcode `invokespecial` chỉ dựa vào từ khóa `private`; cần kiểm tra class file thực và quy tắc JVM tương ứng.

### 5.1. Constructor: `new` khác `invokespecial`

Khi viết:

~~~java
Child child = new Child();
~~~

Bytecode thường có dạng:

~~~text
new           #... // class Child
dup
invokespecial #... // Method Child."<init>":()V
astore_1
~~~

- `new` cấp phát instance mới (sau khi các điều kiện initialization liên quan được thỏa mãn) và tạo **uninitialized object reference** ở mức verifier.
- `dup` giữ một bản giá trị tham chiếu trên operand stack để dùng sau khi gọi constructor.
- `invokespecial Child.<init>` thực thi instance initialization method; trình kiểm chứng JVM đặt những ràng buộc riêng lên cách sử dụng tham chiếu chưa khởi tạo.
- `astore_1` chỉ là ví dụ về việc lưu reference đã khởi tạo vào local slot; compiler có thể bố trí khác.

**Chỉ `invokespecial` được phép gọi instance initialization method `<init>`** theo class-file constraints. Constructor không phải là method được override; một constructor không thể virtual-dispatch tới “constructor override” ở subclass.

### 5.2. Super call bỏ qua override theo ngữ nghĩa nguồn

Ví dụ:

~~~java
class Parent {
    String label() { return "PARENT"; }
}
class Child extends Parent {
    @Override String label() { return "CHILD"; }
    String parentLabel() { return super.label(); }
}
~~~

Với một `Child`, gọi `child.label()` từ bên ngoài là virtual call thông thường và chọn `Child.label()`. Nhưng bên trong `Child.parentLabel()`, biểu thức `super.label()` có ngữ nghĩa gọi implementation phía superclass, nên compiler sinh `invokespecial` phù hợp.

Không diễn dịch `invokespecial` là “lúc nào cũng gọi chính xác class được viết trong constant pool mà không có quy tắc lựa chọn”. Đặc tả có **special lookup/selection**, gồm ảnh hưởng của `ACC_SUPER` và quan hệ thừa kế. Điều cần nhớ ở cấp học tập là **nó không thực hiện dispatch override thông thường giống invokevirtual**.

### 5.3. Interface default method qua `Interface.super`

Với một direct superinterface thích hợp, biểu thức `Speaker.super.banner()` gọi default implementation từ interface ấy, thường được biên dịch bằng `invokespecial` với **InterfaceMethodref**. Điều này **khác** lời gọi qua biến `Speaker`, vốn có thể dùng `invokeinterface`.

Nhờ đó, dù cùng là method của interface, **cách gọi** (`Speaker.super.banner()` hay `speaker.banner()`) có thể quyết định instruction khác nhau.

## 6. `invokevirtual`: dispatch dựa vào object runtime

Đây là lệnh gọi instance method rất phổ biến đối với **class-typed receiver**.

~~~java
Parent value = new Child();
System.out.println(value.label());
~~~

Compiler thấy biến `value` có kiểu khai báo `Parent` và thông thường sinh symbolic reference `Parent.label:()Ljava/lang/String;`, nhưng object thực là `Child`. Khi chạy, `invokevirtual` thực hiện lựa chọn implementation theo **runtime class của receiver**, do đó có thể chọn `Child.label()`.

~~~text
Biên dịch (theo kiểu khai báo)              Thực thi (theo object thực)
Parent value;                                receiver = instance Child
value.label()  ── invokevirtual Parent.label ──> Child.label()
~~~

**Chú ý:**

- `invokevirtual` vẫn có thể xuất hiện khi method là `final` hoặc không thực sự có subclass override nào. Opcode biểu diễn chế độ gọi, **không chứng minh** runtime luôn cần tìm kiếm phức tạp.
- `private` method không được override như virtual instance method thông thường. Java compiler và JVM có quy tắc riêng, cần nhìn bytecode cụ thể khi phân tích trường hợp private/nestmate.
- `static` method không nằm trong virtual dispatch. Static method bị hide chứ không được override.
- Nếu receiver value là `null`, lời gọi instance cần dereference receiver sẽ gây `NullPointerException`, không có implementation nào được chọn để chạy.
- `Object.equals`, `toString`, `hashCode` là ví dụ dễ thấy về virtual method; với class override hợp lệ, method thực thi có thể là của subclass. Riêng quy tắc chọn implementation vẫn dựa vào class runtime và luật method selection.

### Overload được chọn trước, override được chọn sau

~~~java
class Parent {
    String pick(Object value) { return "OBJECT"; }
}
class Child extends Parent {
    @Override String pick(Object value) { return "CHILD_OBJECT"; }
    String pick(String value) { return "CHILD_STRING"; }
}
~~~

Với `Parent p = new Child();`, biểu thức `p.pick("JVM")` được compiler chọn **overload `pick(Object)`** vì kiểu khai báo `Parent` không cung cấp `pick(String)`. Khi chạy, `invokevirtual` chọn override **`Child.pick(Object)`**, không “đổi overload” sang `Child.pick(String)` vì object thực là Child. Descriptor đã được xác định tại compile time; dispatch lựa chọn method **cùng tên/descriptor phù hợp** theo luật override.

Điểm này giải thích vì sao “động” của virtual dispatch không có nghĩa Java hoãn **mọi quyết định về method** cho tới runtime.

## 7. `invokeinterface`: gọi qua hợp đồng interface

Khi một biến có **kiểu interface**, bytecode thường sử dụng `invokeinterface`:

~~~java
Speaker speaker = new Child();
String message = speaker.say();
~~~

Compiler tham chiếu symbolic method của `Speaker`, còn JVM tìm method phù hợp mà **class runtime** của receiver cung cấp hoặc kế thừa. Ở đây `Child.say()` thực thi.

Sự khác nhau với `invokevirtual` không phải “interface không có code”: từ Java 8 interface có thể có **default method**, và từ Java 9 còn có private method trong interface. `invokeinterface` vẫn cần tuân theo quy tắc resolution/selection để tìm implementation, bao gồm các trường hợp method được class kế thừa, method của `Object` theo quy tắc cho phép, và default method.

### 7.1. Default method

Nếu interface khai báo:

~~~java
interface Speaker {
    String say();
    default String banner() {
        return "INTERFACE_DEFAULT";
    }
}
~~~

Thì lời gọi `speaker.banner()` thông qua reference `Speaker` có thể dùng `invokeinterface`, và runtime có thể chọn default implementation nếu class không cung cấp override ưu tiên hơn. Ngược lại, `Speaker.super.banner()` bên trong class triển khai được phép gọi qua `invokespecial`.

Cần biết: Java có những quy tắc giải quyết xung đột default methods khi nhiều superinterface đưa ra cùng chữ ký. Compiler thường phát hiện trường hợp không hợp lệ; class file được thay đổi hay binary mismatch có thể dẫn tới linkage/invocation errors theo đặc tả. Không nên tự viết mô hình “luôn chọn interface đứng trước trong danh sách implements”.

### 7.2. Layout instruction `invokeinterface`

Ở bytecode, `invokevirtual`, `invokespecial`, `invokestatic` có dạng opcode cộng **2 byte constant-pool index**. `invokeinterface` có **5 byte**:

~~~text
invokeinterface indexbyte1 indexbyte2 count 0
~~~

- Hai byte `indexbyte1/indexbyte2` tạo constant-pool index tới InterfaceMethodref.
- Byte `count` ghi số đơn vị local-variable slot cần cho **receiver và các đối số**; `long`/`double` chiếm hai slot.
- Byte cuối phải bằng 0 theo class-file format.

Ví dụ `javap` có thể in:

~~~text
invokeinterface #49,  1  // InterfaceMethod Speaker.say:()Ljava/lang/String;
~~~

Với method không nhận tham số nguồn, `count = 1` vẫn hợp lý vì có **receiver**. Không suy ra byte `count` là số phương thức trong interface, số interface của object hay số lượng tham số Java bỏ qua receiver.

## 8. Bài thực hành đầy đủ trên Java 17+

Tạo file **`InvokeModesDemo.java`** trong thư mục làm việc:

~~~java
public final class InvokeModesDemo {
    interface Speaker {
        String say();

        default String banner() {
            return "INTERFACE_DEFAULT";
        }

        static String version() {
            return "SPEAKER_STATIC";
        }
    }

    static class Parent {
        String label() {
            return "PARENT";
        }

        static String kind() {
            return "PARENT_STATIC";
        }
    }

    static class Child extends Parent implements Speaker {
        @Override
        String label() {
            return "CHILD";
        }

        @Override
        public String say() {
            return "CHILD_SAYS";
        }

        String parentLabel() {
            return super.label();
        }

        String interfaceBanner() {
            return Speaker.super.banner();
        }

        static String kind() {
            return "CHILD_STATIC";
        }
    }

    static int twice(int n) {
        return n * 2;
    }

    public static void main(String[] args) {
        Parent asParent = new Child();
        Speaker asSpeaker = (Speaker) asParent;
        Child exact = (Child) asParent;

        System.out.println("static=" + twice(3));
        System.out.println("classStatic=" + Parent.kind());
        System.out.println("hiddenStatic=" + Child.kind());
        System.out.println("virtual=" + asParent.label());
        System.out.println("interface=" + asSpeaker.say());
        System.out.println("super=" + exact.parentLabel());
        System.out.println("default=" + exact.interfaceBanner());
        System.out.println("interfaceStatic=" + Speaker.version());
    }
}
~~~

Biên dịch/chạy với **JDK 17 trở lên**:

~~~bash
javac --release 17 InvokeModesDemo.java
java InvokeModesDemo
~~~

**Stdout chính xác:**

~~~text
static=6
classStatic=PARENT_STATIC
hiddenStatic=CHILD_STATIC
virtual=CHILD
interface=CHILD_SAYS
super=PARENT
default=INTERFACE_DEFAULT
interfaceStatic=SPEAKER_STATIC
~~~

Mã đã được biên dịch thực tế bằng `javac --release 17` trên JDK 21 và chạy với kết quả đúng như trên. Các nested class được compiler xuất thành những file `InvokeModesDemo$Parent.class`, `InvokeModesDemo$Child.class`, `InvokeModesDemo$Speaker.class` (trong tên file có ký tự `$`), bên cạnh `InvokeModesDemo.class`.

### 8.1. Xem bytecode của class chính

~~~bash
javap -c -p InvokeModesDemo
~~~

Hãy tìm các lệnh trong `main`:

| Biểu thức nguồn | Instruction cần tìm trong ví dụ |
| --- | --- |
| `new Child()` | `new`, `dup`, `invokespecial Child."<init>":()V` |
| `twice(3)` | `invokestatic InvokeModesDemo.twice:(I)I` |
| `Parent.kind()` | `invokestatic Parent.kind:()Ljava/lang/String;` |
| `Child.kind()` | `invokestatic Child.kind:()Ljava/lang/String;` |
| `asParent.label()` | `invokevirtual Parent.label:()Ljava/lang/String;` |
| `asSpeaker.say()` | `invokeinterface Speaker.say:()Ljava/lang/String;` |
| `exact.parentLabel()` | `invokevirtual Child.parentLabel:()Ljava/lang/String;` |
| `exact.interfaceBanner()` | `invokevirtual Child.interfaceBanner:()Ljava/lang/String;` |
| `Speaker.version()` | `invokestatic Speaker.version:()Ljava/lang/String;` |

**Phân biệt cẩn thận:** `main` gọi `exact.parentLabel()` bằng `invokevirtual`, nhưng **bên trong** `parentLabel()` có lời gọi `super.label()` dùng `invokespecial`. Tương tự, `main` gọi `exact.interfaceBanner()` bằng `invokevirtual`, còn **bên trong method ấy** dùng `invokespecial` cho `Speaker.super.banner()`. Không kết luận opcode của toàn bộ một chuỗi lời gọi chỉ từ kết quả stdout.

### 8.2. Xem bytecode trong nested class bằng javap

Ở **Bash**, dùng dấu nháy đơn để shell không mở rộng ký tự `$` trong tên class:

~~~bash
javap -c -p 'InvokeModesDemo$Child'
javap -c -p 'InvokeModesDemo$Parent'
javap -c -p 'InvokeModesDemo$Speaker'
~~~

Trong **PowerShell**, các tên class có `$` cũng nên đặt trong nháy đơn, ví dụ:

~~~powershell
javap -c -p 'InvokeModesDemo$Child'
~~~

Trong output `Child`, tìm:

~~~text
InvokeModesDemo$Child();
  ...
  invokespecial ... // Method InvokeModesDemo$Parent."<init>":()V

java.lang.String parentLabel();
  ...
  invokespecial ... // Method InvokeModesDemo$Parent.label:()Ljava/lang/String;

java.lang.String interfaceBanner();
  ...
  invokespecial ... // InterfaceMethod InvokeModesDemo$Speaker.banner:()Ljava/lang/String;
~~~

Đây là **trích ý nghĩa cần tìm**, không phải toàn bộ output `javap` gồm constant-pool indices/bytecode offsets cố định. `javap -c` trên JDK 21 khi chạy bản đã biên dịch ở trên đã xác nhận cả ba trường hợp.

### 8.3. Điều gì còn xuất hiện ngoài bốn opcode?

Trong `main`, Java 17 compiler có thể dùng:

- `getstatic java/lang/System.out` để lấy `PrintStream`.
- `checkcast` khi ép `Parent` thành `Speaker` hoặc `Child`.
- `invokevirtual PrintStream.println` để in chuỗi.
- `invokedynamic makeConcatWithConstants` để nối `"static=" + twice(3)` và các chuỗi output.
- `aload` và `astore` để di chuyển reference giữa local variables với operand stack.

Các instruction này giúp tạo bối cảnh đầy đủ của lời gọi. Khi bạn thấy `invokedynamic` cho nối chuỗi, **đừng nhầm nó là opcode gọi `twice` hay `label`**; những method đó vẫn được gọi bằng `invokestatic` hoặc `invokevirtual` trong bản biên dịch đã kiểm tra.

## 9. Nhìn từ một lời gọi: compile time, link time và run time

Xét lời gọi `asParent.label()` trong ví dụ. Có ba mốc:

### Bước 1 — Chọn overload và tên/descriptor lúc biên dịch

Compiler nhìn kiểu khai báo `Parent`, kiểm tra method accessible và quyết định tham chiếu `label:()Ljava/lang/String;`. Nếu có nhiều overload, compiler chọn overload phù hợp với kiểu biểu thức và luật overload resolution của Java **trước khi chạy**.

### Bước 2 — JVM resolve symbolic method reference

Khi thực thi, JVM cần class/method descriptor thích hợp và bảo đảm các điều kiện liên kết/truy cập. Resolution có thể diễn ra lúc linking hoặc khi instruction cần dùng tùy những điều JVM cho phép. Đây là việc nhận diện một symbolic reference thực sự tương ứng với phần tử runtime hợp lệ.

### Bước 3 — Chọn implementation theo receiver thực

Receiver của `asParent` là instance `Child`; `invokevirtual` theo rules method selection sẽ chạy `Child.label()`, vì đây là override hợp lệ. **Resolution tới method được biểu thị từ Parent không có nghĩa bỏ qua override của Child.**

Đối với `invokestatic Parent.kind()`, không có bước dispatch theo receiver. Với `invokespecial super.label()`, áp dụng special selection. Với `invokeinterface Speaker.say()`, JVM xét implementation khả dụng từ runtime class `Child` cùng quy tắc interface method.

### Method lookup không đồng nghĩa call stack không thay đổi

Dù là `invokestatic` hay `invokevirtual`, khi vào body callee, về mặt ngữ nghĩa JVM có **lời gọi method và frame tương ứng**, tham số/receiver được chuyển vào locals, rồi method trả về hoặc ném exception. Khi JIT inline, các frame vật lý/nội bộ có thể được tối ưu; điều đó không thay đổi kết quả điều khiển luồng và những gì chương trình Java được phép quan sát.

## 10. Trường hợp method là `null`, `final`, `abstract` hoặc không tồn tại

### 10.1. Null receiver

~~~java
Parent value = null;
// value.label(); // nếu chạy: NullPointerException
~~~

Với instance call cần receiver, `invokevirtual` và `invokeinterface` yêu cầu reference không null. `invokespecial` gọi instance method cũng cần receiver hợp lệ theo quy tắc invocation, ngoại trừ các chi tiết verifier đặc thù của uninitialized reference khi xây dựng object.

`invokestatic` **không cần receiver** và không thể ném NullPointerException chỉ vì “receiver của lời gọi static là null”; source có thể đánh giá expression dùng cú pháp gọi static qua instance, nhưng method static vẫn không có `this` và không có dynamic receiver dispatch. Đừng dùng cú pháp ấy trong code mới.

### 10.2. Final method

Một instance method `final` không thể được override hợp lệ ở subclass. Compiler vẫn có thể sinh `invokevirtual` để gọi nó theo bytecode. JIT thường có cơ hội tối ưu khi biết target, nhưng không được dùng sự hiện diện của `invokevirtual` để kết luận có nhiều override khả thi.

### 10.3. Abstract method và interface implementation

Một class có thể khai báo abstract method; object hợp lệ của một concrete subclass phải cung cấp implementation theo các quy tắc Java. Nếu binary không tương thích khiến method được chọn tại runtime không có implementation phù hợp, JVM có thể ném **`AbstractMethodError`**. Đây là **linkage error** và không nên giải thích đơn giản là `NullPointerException`.

### 10.4. Class/interface API thay đổi giữa compile và runtime

Ví dụ, caller từng biên dịch dựa trên `Parent.label:()Ljava/lang/String;`, nhưng thư viện runtime xóa/đổi method hay descriptor. Caller có thể chạy tới lúc gặp `NoSuchMethodError` hoặc lỗi liên kết khác. Đổi một method từ instance sang static (hoặc ngược lại) phá tính tương thích của opcode và thường dẫn tới **`IncompatibleClassChangeError`** trong hoàn cảnh phù hợp.

| Lỗi hay gặp | Điều cần kiểm tra |
| --- | --- |
| `NullPointerException` khi gọi instance | Receiver thực có `null` không? |
| `NoSuchMethodError` | Binary runtime có đúng tên và descriptor caller đã biên dịch không? |
| `IllegalAccessError` | Quyền truy cập hoặc module/package/nest relationship có thay đổi khiến runtime không còn truy cập hợp lệ? |
| `IncompatibleClassChangeError` | Caller mong static/instance, class/interface hoặc cấu trúc kế thừa khác với binary đang chạy? |
| `AbstractMethodError` | Method được chọn có implementation hợp lệ không? |
| `StackOverflowError` | Đệ quy/call chain quá sâu so với khả năng stack của thread? |

Đây là hướng điều tra, **không phải ánh xạ một-một bảo đảm cho mọi lỗi**. Xem stack trace, classpath/module path, file class thực được load, các loader và version dependency trước khi kết luận.

## 11. Method override, default method và quyền truy cập

### Override phải hợp lệ theo Java/JVM

Với `@Override`, compiler giúp xác minh chữ ký của instance method mới tương thích với method muốn override. Không phải method cùng tên đều override: private method, static method, constructor và method overload có luật khác nhau.

Về mặt JVM, override phụ thuộc tên, descriptor cùng những điều kiện như accessibility, quan hệ lớp và các luật method overriding/selection. Khi có generic erasure hoặc covariant return, compiler có thể tạo **bridge method** để giữ ABI nhất quán; bạn có thể thấy thêm method có cờ `ACC_BRIDGE`/`ACC_SYNTHETIC` trong `javap -v`. Vì vậy không thể luôn ánh xạ “một khai báo method trong Java” thành “đúng một method_info JVM”.

### Default method của interface có thể được class override

Nếu `Child` tự khai báo:

~~~java
@Override
public String banner() {
    return "CHILD_BANNER";
}
~~~

thì lời gọi qua `Speaker speaker = new Child(); speaker.banner()` có thể chọn `Child.banner()` thay vì default implementation. Tuy nhiên, một lời gọi có chủ đích `Speaker.super.banner()` bên trong class được phép dùng nó vẫn gọi nhánh default phù hợp qua special invocation.

**Đoạn trên chỉ minh họa sự thay đổi so với chương trình thực hành**; không thêm nó vào file mẫu nếu muốn giữ stdout của phần 8. Nếu thêm override và biên dịch lại, kết quả gọi thường sẽ đổi theo method implementation mới.

### Access và module boundary

Một phương thức tồn tại ở class file nhưng caller vẫn không được phép gọi nếu quy tắc truy cập Java/JVM không cho. Method có thể `public`, `protected`, package-private hoặc `private`, và trong dự án module Java còn có điều kiện module accessibility. Đừng suy luận rằng cứ `javap -p` nhìn thấy method thì có thể gọi hợp lệ từ mọi class.

## 12. Những cách tối ưu của JIT không làm thay đổi ý nghĩa lệnh

HotSpot hoặc JVM khác có thể dùng **inline**, **devirtualization**, **inline caches**, profile-guided optimization hoặc kỹ thuật lookup khác để tăng tốc. Khi runtime thấy một call site `invokevirtual` trong một khoảng thời gian chỉ gặp một kiểu object, JIT có thể tối ưu đường gọi và có cơ chế fallback/deoptimization khi giả định không còn phù hợp.

Các thuật ngữ này giúp đọc tài liệu performance, nhưng không sửa ý nghĩa của `invokevirtual`: override hợp lệ của receiver runtime vẫn phải được tôn trọng. Tương tự, `invokeinterface` không mặc nhiên chậm hơn `invokevirtual` trong mọi workload và mọi JVM; mức tối ưu phụ thuộc môi trường, kiểu dữ liệu và số lượng implementation thực tế. Khi muốn so sánh hiệu năng, hãy đo bằng benchmark đáng tin (ví dụ JMH), tránh dựa vào cách đếm opcode đơn thuần.

### Exception và synchronized method

Nếu method được gọi là `synchronized`, JVM có quy tắc monitor entry/exit khi invoke/return hoặc hoàn tất bất thường. Nó không thay đổi “static hay virtual” của method, nhưng ảnh hưởng cơ chế đồng bộ của callee. Nếu callee ném exception, caller có thể bắt theo exception table; việc exception lan truyền không biến `invokevirtual` thành `invokespecial`.

## 13. Cách đọc `javap` để kiểm chứng phương thức được gọi

Trong project có một class bất kỳ, làm theo thứ tự:

1. **Biên dịch đúng Java target** (ví dụ `javac --release 17 ...`) và bảo đảm class file được đọc chính là binary đang chạy.
2. Dùng `javap -c -p TenClass` để tìm method chứa call site. Lưu ý `javap` có thể in cả `<init>`, synthetic/bridge method và code do compiler sinh.
3. Quan sát **opcode + constant-pool reference + descriptor**: `invokestatic`, `invokespecial`, `invokevirtual` hay `invokeinterface`.
4. Xác định **object receiver** (nếu có) qua `aload`, `getfield`, `new`, `checkcast` và các instruction trước call site.
5. Với `invokevirtual`/`invokeinterface`, xác định **runtime class** của object để hiểu implementation được chọn; chỉ đọc tên method ở comment của `javap` chưa đủ suy ra target cuối cùng.
6. Dùng `javap -v` khi cần xem Methodref/InterfaceMethodref, flags như `ACC_STATIC`, `ACC_BRIDGE`, signature, class-file version và metadata.
7. Nếu runtime báo linkage error, đối chiếu class file đã **load tại runtime** và dependency tree với class mà caller **được biên dịch dựa trên**.

Điểm kỹ thuật quan trọng: các số như `#45` sau `invokevirtual` là **constant-pool index**, không phải địa chỉ native method. Các số đầu dòng như `65:` là **bytecode PC** trong method, không phải dòng Java hoặc địa chỉ vật lý.

## 14. Các hiểu lầm thường gặp

1. **“`invokestatic` luôn cần một object để tìm method.”** Sai, static call không có receiver và không có `this`.
2. **“`invokevirtual` gọi đúng method của kiểu khai báo, không quan tâm object thật.”** Sai, nó sử dụng method selection theo runtime class khi override hợp lệ.
3. **“`invokeinterface` chỉ chạy code trong interface, không thể chạy class method.”** Sai, runtime tìm implementation thích hợp của object; interface default method là một trong những trường hợp.
4. **“Constructor được gọi qua `invokevirtual` như method bình thường.”** Sai, instance initializer `<init>` chỉ được gọi qua `invokespecial`.
5. **“`super.method()` vẫn dispatch tới override ở class con.”** Sai theo ngữ nghĩa `super`; nó dùng đường special invocation.
6. **“`invokeinterface` có `count=1` nghĩa là một tham số Java.”** Sai, count bao gồm receiver và tính slot của đối số.
7. **“Method static ở class con override static method cha.”** Sai, Java dùng **hiding** cho static.
8. **“Overload resolution được JVM tự đổi theo class runtime.”** Sai, overload đã được chọn theo kiểu biểu thức lúc compile; virtual dispatch chỉ tìm override phù hợp.
9. **“`final` method không thể xuất hiện sau opcode `invokevirtual`.”** Sai, opcode không đồng nghĩa chắc chắn có subclass override.
10. **“Mọi private instance call nhất thiết được biên dịch thành `invokespecial` ở mọi JDK.”** Sai, chi tiết encoding còn phụ thuộc phiên bản JVM/compiler và ngữ cảnh truy cập.
11. **“Vtable/itable của HotSpot là phần bắt buộc của JVMS.”** Sai, spec định nghĩa **ngữ nghĩa**, implementation tự chọn cấu trúc tối ưu.
12. **“Có method trùng tên và compile thành công là chắc chạy trên mọi phiên bản thư viện.”** Sai, runtime descriptor, access và loại method có thể đã thay đổi.

## 15. Câu hỏi tự kiểm tra

1. Khi gọi `Math.max(4,7)`, opcode thường thấy là gì và có receiver không? **`invokestatic`; không có receiver**.
2. Khi `Parent p = new Child(); p.label()` và `Child` override `label()`, method nào chạy? **`Child.label()`**, do `invokevirtual` chọn theo runtime class.
3. Lời gọi `super.label()` từ một method của `Child` thường dùng lệnh nào? **`invokespecial`**, theo super invocation semantics.
4. Constructor `<init>` được gọi bằng lệnh nào? **`invokespecial`**, không phải `invokevirtual`.
5. Khi biến khai báo kiểu `Speaker` gọi `say()`, compiler thường dùng opcode gì? **`invokeinterface`**.
6. Tại sao `Speaker.version()` không dùng `invokeinterface`? Vì đây là **static interface method**, được gọi bằng `invokestatic`.
7. `Parent.kind()` và `Child.kind()` cùng tên có dynamic override không? **Không**, đó là static method hiding.
8. Với `Parent p = new Child()`, nếu Parent chỉ khai báo `pick(Object)` còn Child thêm overload `pick(String)`, `p.pick("x")` dùng overload nào? **`pick(Object)` đã được compiler chọn**, sau đó dispatch tới override phù hợp của nó.
9. `invokeinterface` gọi method không có tham số nguồn thì byte `count` có thể bằng 1 không? **Có**, vì gồm receiver.
10. Tại sao `javap` có thể in `invokedynamic` dù đang học bốn lệnh trên? **Compiler Java hiện đại dùng invokedynamic cho một số call site, chẳng hạn nối chuỗi**.
11. Có thể kết luận method nào thực sự chạy chỉ từ comment `// Method Parent.label...` của `invokevirtual` không? **Chưa đủ**; cần runtime receiver type và rules selection.
12. Một call site ban đầu được JIT inline có thay đổi ý nghĩa override của Java không? **Không**, tối ưu phải bảo toàn ngữ nghĩa dispatch.

## 16. Tài liệu chuẩn

- JVM Specification Java SE 17, mục **Method Invocation and Return Instructions**: https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-2.html#jvms-2.11.8
- JVMS, **Instruction Set** (`invokestatic`, `invokespecial`, `invokevirtual`, `invokeinterface`): https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-6.html
- JVMS, **Method Resolution, Interface Method Resolution, Method Selection**: https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-5.html
- JVMS, **Class-file Constraints** và constant pool: https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-4.html
- Java Language Specification, **Method Invocation Expressions**: https://docs.oracle.com/javase/specs/jls/se17/html/jls-15.html#jls-15.12
- `javap` của JDK 17: https://docs.oracle.com/en/java/javase/17/docs/specs/man/javap.html

**Kết luận kỹ thuật:** `invokestatic` gọi static method **không có receiver**; `invokespecial` dùng cho constructor và những lời gọi instance có **special selection**, nổi bật là `super`; `invokevirtual` chọn override theo **runtime class** của object; còn `invokeinterface` thực hiện lời gọi qua **interface contract**. Để hiểu chính xác một lời gọi JVM, cần đọc **opcode**, **method reference + descriptor**, **receiver/runtime type**, và phân biệt **resolution** với **method selection**.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="stack-frames-and-calls-004">Phân giải phương thức và chọn phương thức theo kiểu thực của đối tượng</a>

<details>
<summary>Xem chi tiết</summary>

# Phân giải phương thức và chọn phương thức theo kiểu thực của đối tượng trong JVM

Khi một biến Java khai báo kiểu cha nhưng trỏ tới đối tượng lớp con, lời gọi phương thức instance có thể chạy phần triển khai của lớp con. Ví dụ `Animal animal = new Dog(); animal.sound();` có thể gọi `Dog.sound()`, dù tên phương thức trong bytecode của đoạn gọi được tham chiếu qua `Animal`. Để giải thích chính xác hành vi này phải tách **phân giải tham chiếu phương thức (method resolution)** và **lựa chọn phương thức để thực thi (method selection/dispatch)**.

Trong JVM, hai công việc ấy không phải một thao tác: **resolution** tìm một phương thức phù hợp với tham chiếu ký hiệu trong constant pool; còn **selection** ở `invokevirtual`/`invokeinterface` xét **kiểu runtime của đối tượng receiver** và phương thức đã được phân giải để chọn cách triển khai thích hợp. Đó là nền tảng cho overriding và đa hình động. Bài tập trung vào cơ chế JVM Java SE 17, liên hệ với cách `javac` chọn overload, mã lệnh gọi, JVM frames, interface default methods, bridge methods và các trường hợp không dispatch động.

## 1. Ba khái niệm cần phân biệt

| Giai đoạn | Câu hỏi phải trả lời | Dữ liệu được dùng |
| --- | --- | --- |
| **Compile-time method lookup / overload resolution** (JLS `15.12`) | Trong các tên trùng nhau, lời gọi source chọn **chữ ký nào**? | Kiểu tĩnh của biểu thức, kiểu đối số, khả năng áp dụng overload, generic inference và quy tắc chọn most-specific |
| **JVM symbolic reference resolution** (JVMS `5.4.3`) | Constant-pool method reference đang gọi được gắn với method nào, có hợp lệ về class/interface, tên, descriptor và quyền truy cập? | Methodref/InterfaceMethodref, class loader, hệ thống class/interface, name + descriptor và kiểm tra liên kết |
| **Run-time method selection** (JVMS `5.4.6`) | Với `invokevirtual` hoặc `invokeinterface`, object thực sự cần chạy implementation nào? | **Run-time class của receiver**, phương thức đã resolve và quy tắc overriding/inheritance |

Ví dụ một biểu thức `animal.sound()`:

1. Compiler biết `animal` có kiểu tĩnh `Animal`, xác định lời gọi `sound()` hợp lệ và descriptor trả về phù hợp.
2. Trong class file, invocation tham chiếu ký hiệu kiểu như `Animal.sound:()Ljava/lang/String;`.
3. JVM phân giải method reference đó theo quy tắc liên kết.
4. Khi chạy lệnh `invokevirtual` trên object thực `Dog`, JVM chọn implementation `Dog.sound()` nếu đó là override hợp lệ.
5. JVM tạo frame/cơ chế gọi tương ứng và chuyển điều khiển tới code đã chọn.

**Kết luận then chốt:** kiểu tĩnh quyết định **lời gọi nào hợp lệ và overload nào được mã hóa**; kiểu runtime quyết định **override nào được chạy** đối với lời gọi virtual thích hợp. Không phải mọi opcode gọi method đều dùng bước lựa chọn này.

## 2. Method reference trong class file có những thông tin gì?

Tệp `.class` có một **constant pool** chứa các tham chiếu ký hiệu. Tham chiếu tới phương thức class thường dùng `CONSTANT_Methodref_info`; tham chiếu tới phương thức interface thường dùng `CONSTANT_InterfaceMethodref_info`. Mỗi tham chiếu mang ý nghĩa:

- Một class/interface được tham chiếu bằng tên nhị phân.
- **Tên phương thức**, ví dụ `sound` hoặc `get`.
- **Method descriptor** ghi kiểu tham số và kiểu trả về ở mức JVM.
- Kết hợp các thành phần thành tham chiếu cần resolution, **chưa phải địa chỉ máy** hay con trỏ cố định tới instruction.

Các descriptor minh họa:

| Java method | JVM descriptor |
| --- | --- |
| `String sound()` | `()Ljava/lang/String;` |
| `int add(int a, int b)` | `(II)I` |
| `void accept(Object o)` | `(Ljava/lang/Object;)V` |
| `long calculate(int x, long y)` | `(IJ)J` |
| `String[] split(String text)` | `(Ljava/lang/String;)[Ljava/lang/String;` |

Quy tắc ký hiệu: `I` = `int`, `J` = `long`, `V` = `void`, `L...;` là class/interface reference, dấu `[` là array. **Descriptor JVM có cả kiểu trả về**, còn Java source **không** cho phép overload chỉ khác kiểu trả về. Đó là lý do cần phân biệt “source method signature” với “JVM method descriptor”, đặc biệt khi đọc bridge methods do compiler sinh.

Ví dụ trong disassembly `javap -c -v`:

```text
invokevirtual #N  // Method DispatchWalkthrough$Animal.sound:()Ljava/lang/String;
```

`#N` ở đây là ký hiệu chỗ đứng cho **index thật** của constant pool; đừng coi `N` là một số cố định hoặc là phần output thực tế. Ở `javap`, đích của symbolic reference có thể là `Animal.sound` dù runtime chọn `Dog.sound`. Việc thấy tên lớp cha trong constant pool **không bác bỏ** dispatch động.

### Method resolution không đồng nhất với class initialization

- Loading chuẩn bị biểu diễn kiểu; linking gồm verification, preparation và resolution; initialization chạy `<clinit>` khi các điều kiện kích hoạt xảy ra.
- Method resolution tìm method được tham chiếu và kiểm tra hợp lệ của liên kết; class initialization là quá trình khác. `invokestatic` có thể khiến class/interface được initialize theo quy tắc riêng.
- JVMS cho phép implementation thực hiện resolution theo chiến lược phù hợp (ví dụ chủ động hoặc khi cần); đừng khẳng định mọi tham chiếu method “chỉ được resolve đúng lần đầu gọi” hoặc “bắt buộc resolve hết lúc khởi động”.
- JVM có thể cache kết quả resolution hoặc tối ưu call site; đó là chi tiết triển khai. Bảo đảm được quy định nằm ở **hành vi quan sát được**.

## 3. Java chọn overload tại compile time, rồi JVM mới chọn override

Hai từ thường bị nhầm:

- **Overloading:** nhiều method cùng tên nhưng khác danh sách tham số; compiler chọn method áp dụng và phù hợp nhất theo **kiểu tĩnh** tại nơi gọi. Đây không phải runtime dispatch theo kiểu thực của đối số.
- **Overriding:** lớp con cung cấp implementation thay thế cho một **instance method** được kế thừa, với điều kiện tương thích về chữ ký, quyền truy cập, kiểu trả về và các quy tắc Java liên quan. Khi lời gọi virtual đến receiver thuộc lớp con, implementation override có thể được chọn.

Ví dụ:

```java
class Animal { }
class Dog extends Animal { }

static String describe(Animal a) { return "Animal"; }
static String describe(Dog d) { return "Dog"; }

Animal value = new Dog();
System.out.println(describe(value)); // Animal
```

Đây là **đoạn trích minh họa**, đặt trong một class thích hợp để biên dịch. `describe(value)` chọn overload `describe(Animal)` vì tại source `value` có kiểu **Animal**. JVM không “đổi overload” thành `describe(Dog)` khi phát hiện object runtime là Dog. Muốn dùng overload khác phải viết lời gọi có kiểu tĩnh tương ứng (ví dụ cast hợp lệ), hoặc dùng cơ chế dispatch theo đối tượng chủ động.

Ngược lại:

```java
Animal value = new Dog();
System.out.println(value.sound()); // Giả sử Dog override sound()
```

Compiler chọn method có descriptor `sound()` trên kiểu tĩnh `Animal`; sau resolution, `invokevirtual` có thể chọn body override ở `Dog`. Hai bước liên quan **khác loại lựa chọn**.

### Cảnh báo về cast

`((Dog) value).sound()` làm biểu thức có kiểu tĩnh `Dog` và runtime kiểm tra cast. Nhưng ngay cả khi **không cast**, `value.sound()` đã có thể gọi `Dog.sound()` nếu override hợp lệ. Cast không phải điều kiện cần của dynamic dispatch; nó chủ yếu thay đổi những method được phép truy cập tại compile time, overload được chọn, hoặc thực hiện cast check.

## 4. Quy tắc lựa chọn của `invokevirtual` và `invokeinterface`

Sau khi đã resolve method reference, khi thực thi `invokevirtual`/`invokeinterface`, JVM xét **class runtime của receiver** và method đã resolve. Mô hình học tập cho những trường hợp instance method thông thường:

1. Nếu receiver là `null`, lời gọi instance tương ứng gây `NullPointerException`, không có object để chọn implementation.
2. Nếu method đã resolve thuộc trường hợp đặc biệt `ACC_PRIVATE`, selection không diễn ra như một method có thể được override.
3. Với method có thể override, xét class runtime của receiver: có declaration method override phù hợp thì chọn nó.
4. Nếu class ấy không khai báo override, đi lên superclass gần nhất có implementation override phù hợp.
5. Nếu không tìm được trong chuỗi class, xét các **maximally-specific superinterface methods** theo quy tắc JVMS; một default method phù hợp có thể được chọn.
6. Nếu không có implementation cụ thể phù hợp, hoặc có xung đột mặc định đặc biệt do thay đổi binary, việc gọi có thể gây liên kết/lỗi thời gian chạy như `AbstractMethodError` hoặc `IncompatibleClassChangeError`.

Các chi tiết đầy đủ như method access, override rules, interface/Object methods, abstract/default methods và lỗi liên kết được JVMS `5.4.3.3–`5.4.6 và từng `invoke*` quy định. Đừng giản lược selection thành “luôn tìm method đầu tiên trùng tên trên object”: **descriptor và khả năng override hợp lệ** cũng quan trọng.

Ví dụ hierarchy:

```text
Animal
  ├─ sound()       [Animal body]
  └─ eat()         [Animal body]
      ^
      |
     Dog
      └─ sound()   [Dog override]

Animal a = new Dog();
a.sound()  → Dog.sound()
a.eat()    → Animal.eat() (Dog không override)
```

Điểm quan trọng là “method thật được gọi theo runtime type” **không có nghĩa** mọi method thuộc class runtime đều được phép gọi. Một lời gọi như `a.bark()` vẫn bị compiler từ chối nếu `Animal` không khai báo/không thừa kế method `bark()`, dù object runtime là Dog.

## 5. Nhóm opcode invocation: lệnh nào dispatch động?

| Opcode JVM | Cách dùng chính | Quan hệ với kiểu runtime receiver |
| --- | --- | --- |
| `invokevirtual` | Method instance thông thường của class; phần lớn lời gọi có override | **Có selection** dựa trên receiver class và method đã resolve |
| `invokeinterface` | Lời gọi instance thông qua interface method reference | **Có selection** theo receiver, class hierarchy/default interface methods |
| `invokespecial` | Constructor `<init>`, lời gọi `super.m()`, một số lời gọi instance đặc biệt/private | Có các quy tắc tìm đích riêng; **không** sử dụng cơ chế dispatch virtual thông thường |
| `invokestatic` | Method `static` của class hoặc interface | Không có receiver để dispatch theo runtime; tìm method static đã resolve |
| `invokedynamic` | Dynamic call site được liên kết qua bootstrap method, như nhiều lambda/string concat do compiler tạo | Cơ chế liên kết call site riêng, **không** đồng nghĩa “invokevirtual chọn override” |

### `invokevirtual`

Nhận **object reference** cùng các đối số trên operand stack, dùng method reference từ constant pool, rồi chọn target theo runtime receiver. Nếu target không phải `native`, thông thường JVM tạo một **frame** cho method được gọi; frame nhận receiver tại local slot 0 và tham số ở các slot kế tiếp. Mô hình frame ở phần sau sẽ minh họa.

### `invokeinterface`

Dùng interface method reference trong constant pool, nhưng object thực thi có runtime class cụ thể. Ví dụ:

```java
Runnable task = new SomeRunnable();
task.run();
```

Ở đây chỉ là **đoạn minh họa**; `SomeRunnable` giả định implements `Runnable` và có method `run`. Compiler có thể sinh `invokeinterface Runnable.run:()V`. Target thực tế được chọn theo class của `task` (hoặc inherited implementation), không chạy bytecode “nằm trong interface” nếu interface method trừu tượng.

### `invokespecial`

Dùng cho những lời gọi có quan hệ ngữ nghĩa đặc biệt, chẳng hạn `new Dog()` cần gọi constructor `<init>`, hoặc `super.sound()` cố ý bắt đầu theo quy tắc superclass. Với class file có `ACC_SUPER`, JVM có thêm quy tắc lookup cho `super`; nói `invokespecial` “chỉ thực thi chính xác method có tên trên constant pool, không có lookup” là quá đơn giản. Nhưng nó **không chọn override từ class runtime receiver như `invokevirtual`**.

### `invokestatic`

Method static thuộc về class/interface được dùng tại compile time/link time, không phải instance. `static` trong subclass có thể **hide** method static superclass, nhưng không override. Gọi `Animal.category()` và `Dog.category()` chọn hai method khác nhau theo tên class trong source. Lời gọi qua biến `Animal ref = new Dog(); ref.category();` hợp lệ trong Java nhưng không nên viết kiểu đó vì dễ khiến người đọc tưởng có dispatch động; mục tiêu vẫn thuộc `Animal`.

### `invokedynamic`

`invokedynamic` liên kết một **call site** qua thông tin bootstrap của constant pool. Nó thường xuất hiện trong bytecode compiler tạo cho lambda hoặc string concatenation trên Java hiện đại. Hành vi cụ thể phụ thuộc bootstrap và target call site; không mặc định áp dụng bảng chọn override của `invokevirtual` cho chính opcode `invokedynamic`, dù method được liên kết sâu hơn có thể thực hiện lời gọi ảo khác.

## 6. Chương trình Java 17: thấy rõ override, overload, static hiding, super và private

Tạo file **`DispatchWalkthrough.java`**:

```java
public class DispatchWalkthrough {

    static class Animal {
        public String sound() {
            return "Animal";
        }

        public String eat() {
            return "Animal.eat";
        }

        public static String category() {
            return "Animal.category";
        }

        private String secret() {
            return "Animal.secret";
        }

        public String reveal() {
            return secret();
        }
    }

    static class Dog extends Animal {
        @Override
        public String sound() {
            return "Dog";
        }

        public String bark() {
            return "woof";
        }

        public static String category() {
            return "Dog.category";
        }

        private String secret() {
            return "Dog.secret";
        }

        public String revealDog() {
            return secret();
        }

        public String callSuper() {
            return super.sound();
        }
    }

    static String describe(Animal value) {
        return "describe(Animal)";
    }

    static String describe(Dog value) {
        return "describe(Dog)";
    }

    public static void main(String[] args) {
        Animal ref = new Dog();
        Dog dog = new Dog();

        System.out.println("virtual=" + ref.sound());
        System.out.println("inherited=" + ref.eat());
        System.out.println("overload=" + describe(ref));
        System.out.println("overloadDog=" + describe(dog));

        // Cố tình minh họa lời gọi static qua reference;
        // code thực tế nên dùng Animal.category() trực tiếp.
        System.out.println("static=" + ref.category());
        System.out.println("super=" + dog.callSuper());
        System.out.println("private=" + ref.reveal());
        System.out.println("dogPrivate=" + dog.revealDog());

        try {
            Animal missing = null;
            missing.sound();
        } catch (NullPointerException ex) {
            System.out.println("null=NPE");
        }
    }
}
```

Biên dịch/chạy trên JDK có `javac` hỗ trợ Java 17:

```bash
javac --release 17 DispatchWalkthrough.java
java DispatchWalkthrough
```

**Stdout**:

```text
virtual=Dog
inherited=Animal.eat
overload=describe(Animal)
overloadDog=describe(Dog)
static=Animal.category
super=Animal
private=Animal.secret
dogPrivate=Dog.secret
null=NPE
```

Giải thích từng dòng:

- `virtual=Dog`: `ref` có kiểu tĩnh `Animal` nhưng object runtime là `Dog`; `Dog.sound()` override nên được chọn.
- `inherited=Animal.eat`: `Dog` không override `eat`, vì vậy body kế thừa từ `Animal` thực thi.
- `overload=describe(Animal)`: `describe(ref)` chọn overload theo kiểu **Animal** đã biết lúc compile.
- `overloadDog=describe(Dog)`: `dog` có kiểu tĩnh `Dog`, chọn overload dành cho Dog.
- `static=Animal.category`: static method được xác định theo kiểu tĩnh `Animal` dù `ref` trỏ tới Dog. Đó là **method hiding**, không phải polymorphic override.
- `super=Animal`: trong `Dog.callSuper()`, `super.sound()` cố ý gọi implementation superclass theo ngữ nghĩa `invokespecial`.
- `private=Animal.secret`: `Animal.reveal()` gọi method **private** của chính `Animal`. `Dog.secret()` có cùng tên nhưng không override method private ấy.
- `dogPrivate=Dog.secret`: body `Dog.revealDog()` gọi method private của `Dog`.
- `null=NPE`: instance receiver `null` không thể được dispatch đến implementation.

`Dog.bark()` tồn tại trên object thật, nhưng thêm `ref.bark();` sẽ **không biên dịch**, vì kiểu tĩnh `Animal` không có method đó. Đa hình động không thay thế kiểm tra kiểu của compiler.

### Đọc bytecode đúng nơi

Chạy disassembler:

```bash
javap -c -p DispatchWalkthrough
javap -c -p 'DispatchWalkthrough$Dog'
javap -v -p 'DispatchWalkthrough$Animal'
```

Các điểm cần quan sát trên class do `javac` tạo:

- Trong `DispatchWalkthrough.main`, lời gọi `ref.sound()` thường hiện với opcode **`invokevirtual`**, tham chiếu tới method kiểu `DispatchWalkthrough$Animal.sound:()Ljava/lang/String;`. Object chạy lúc đó là `Dog` nên kết quả `Dog`.
- Trong `Dog.callSuper()`, phần gọi `super.sound()` dùng **`invokespecial`** đến method của lớp cha.
- Với `describe(...)` và `category()`, vì method là static, bytecode dùng **`invokestatic`** đến method được compiler chọn theo kiểu.
- Constructor `Dog.<init>` gọi constructor `Animal.<init>` với **`invokespecial`**.
- Lời gọi private liên quan có thể có cách biểu diễn bytecode tùy Java release/compiler và quy tắc private method; bản chất vẫn là method **không thể bị override**. Không biến một mẫu disassembly thành khẳng định “mọi private call luôn luôn mang opcode duy nhất X”.

Trong kết quả `javap`, index constant pool `#7`, `#15`… và offset bytecode phụ thuộc build/compiler, nên **không gán cố định** các con số đó cho mọi môi trường. Thêm nữa, các biểu thức nối chuỗi như `"virtual=" + ref.sound()` trên JDK 17 có thể xuất hiện `invokedynamic` phục vụ string concatenation; nó **không làm mất** việc `ref.sound()` được thực thi theo quy tắc lời gọi instance riêng.

## 7. Theo dõi operand stack và frame khi gọi một instance method

Xét riêng:

```java
Animal receiver = new Dog();
String result = receiver.sound();
```

Đây là đoạn **minh họa đặt trong một method**. Một chuỗi bytecode tiêu biểu cho phần lời gọi có thể là:

```text
aload_1                 // nạp reference receiver lên operand stack
invokevirtual #N        // Method Animal.sound:()Ljava/lang/String;
astore_2                // lưu String result vào local slot tương ứng
```

Số index locals và constant pool là **minh họa**, phụ thuộc cách compiler bố trí frame. Luồng ý nghĩa:

1. Frame caller đang tồn tại; local variable chứa reference tới object `Dog`.
2. `aload` sao chép reference lên **operand stack** của caller. Nó không sao chép toàn bộ Dog object.
3. `invokevirtual` đọc symbolic reference và thực hiện các kiểm tra/selection thích hợp. Method đã resolve đóng vai trò điểm bắt đầu, class runtime `Dog` dẫn đến method `Dog.sound`.
4. Các đối số (nếu có) và reference receiver được lấy từ operand stack của caller; với method Java thông thường, frame callee được tạo và nhận receiver trong **local slot 0** (`this`), các tham số ở slots tiếp theo.
5. Code của `Dog.sound` thực thi trong frame callee, trả về `String` reference qua `areturn`; kết quả trở lại operand stack của caller.
6. Caller dùng `astore` hoặc thao tác khác với kết quả.

Với method `static`, `invokestatic` không cần receiver slot 0; tham số đầu tiên bắt đầu ở local 0. Với method instance, tham số `long`/`double` chiếm hai local slots. **Kiểu object được chọn để dispatch không thay đổi vị trí `this` ở slot 0** của frame method được gọi.

### Phân giải một lần hay mỗi invocation?

Đặc tả phân biệt symbolic reference resolution và lựa chọn invocation; JVM có thể cache kết quả resolution/dispatch, dùng bảng method hoặc tối ưu JIT. Trong ví dụ, nhiều lần gọi `receiver.sound()` không buộc JVM phải thực hiện lại mọi lookup bằng thuật toán chậm trong một vòng lặp. Nhưng dù triển khai tối ưu kiểu gì, **kết quả quan sát được phải như ngữ nghĩa chuẩn**.

## 8. Interface methods và default methods: receiver vẫn quyết định implementation

Interface có thể khai báo method trừu tượng hoặc cung cấp **default method**. `invokeinterface` dựa trên reference tới interface method, sau đó selection dựa vào class runtime receiver và cấu trúc triển khai.

Tạo file độc lập **`InterfaceDispatch.java`**:

```java
public class InterfaceDispatch {

    interface Speaker {
        default String text() {
            return "Speaker";
        }
    }

    static class Basic implements Speaker {
        // Kế thừa default method Speaker.text()
    }

    static class Premium extends Basic {
        @Override
        public String text() {
            return "Premium";
        }
    }

    static class Decorated implements Speaker {
        @Override
        public String text() {
            return "Decorated(" + Speaker.super.text() + ")";
        }
    }

    public static void main(String[] args) {
        Speaker first = new Basic();
        Speaker second = new Premium();
        Speaker third = new Decorated();

        System.out.println("first=" + first.text());
        System.out.println("second=" + second.text());
        System.out.println("third=" + third.text());
    }
}
```

```bash
javac --release 17 InterfaceDispatch.java
java InterfaceDispatch
javap -c -p InterfaceDispatch
javap -c -p 'InterfaceDispatch$Decorated'
```

**Stdout của `java InterfaceDispatch`**:

```text
first=Speaker
second=Premium
third=Decorated(Speaker)
```

- `first.text()` có receiver `Basic`; không tìm thấy class override, nên default method `Speaker.text()` thích hợp được dùng.
- `second.text()` có receiver `Premium`; class này override `text` nên chọn body `Premium.text()`.
- `third.text()` chọn `Decorated.text()`; bên trong method này, `Speaker.super.text()` là lời gọi default implementation được chỉ định rõ, thường thể hiện bởi **`invokespecial`** tới method interface phù hợp.
- Ở `InterfaceDispatch.main`, các lời gọi qua biến `Speaker` thường có opcode **`invokeinterface`**.

### Khi class method và interface default cùng tên

Method instance cụ thể phù hợp trong class/superclass được ưu tiên so với default method interface tương ứng. Nếu nhiều default methods từ các interface độc lập gây xung đột khi viết Java source, compiler thường yêu cầu class thực thi cung cấp override để giải quyết. Không nên giả định JVM sẽ tự chọn ngẫu nhiên một default method khi có hai lựa chọn xung đột.

Với class files được sinh từ các bản thư viện **không tương thích** sau binary evolution, những tình huống không còn một target hợp lệ có thể dẫn tới lỗi liên kết như `IncompatibleClassChangeError` hay `AbstractMethodError`; đó là lý do thay đổi API interface cần kiểm tra binary compatibility.

## 9. Overriding phụ thuộc chữ ký và khả năng kế thừa: không chỉ trùng tên

Những điều kiện quyết định một method có override method đã resolve hay không gồm:

- **Tên và chữ ký thích hợp**, với các quy tắc về erasure/subsignature, return-type substitutability và visibility theo JLS.
- **Instance vs static:** static method chỉ có thể hide, không override instance method. Java cấm “override static bằng instance” hoặc ngược lại khi hai method có cùng signature phù hợp.
- **`private`:** method private không được kế thừa để override theo cách thông thường. Class con có thể có method cùng tên độc lập.
- **`final`:** method instance final không cho class con override hợp lệ; JVM có thể tối ưu vì target không bị thay thế bởi subclass theo quy tắc Java.
- **Package-private:** quan hệ override phụ thuộc khả năng kế thừa/khả năng truy cập giữa package và class hierarchy; không thể chỉ so tên rồi kết luận.
- **`abstract`:** method được khai báo nhưng không có body; object cụ thể phải có implementation hợp lệ trước khi việc gọi đó thành công.
- **Interface defaults:** có các quy tắc ưu tiên, maximally-specific và giải quyết xung đột.

### Một phản ví dụ với field

`Field access` thường quyết định field theo kiểu tĩnh (và symbolic reference) của biểu thức, không dùng cùng kiểu override dispatch của instance methods.

```java
class Base {
    String name = "Base-field";
    String who() { return "Base-method"; }
}
class Child extends Base {
    String name = "Child-field";
    @Override String who() { return "Child-method"; }
}
```

Nếu `Base x = new Child();` thì `x.name` đọc field được chọn từ kiểu `Base` và trả `"Base-field"`, còn `x.who()` chạy override `Child.who()`. Đây là **đoạn minh họa** để phân biệt field hiding và method overriding; không phải chương trình độc lập.

## 10. Generics, erasure và bridge methods có thể làm bytecode khác source

Java generics được triển khai với **type erasure**. Khi kiểu trả về ở source là covariant hoặc khi erasure khiến chữ ký bytecode khác nhau, compiler có thể sinh **bridge method** mang flags `ACC_BRIDGE` và `ACC_SYNTHETIC` để giữ đúng hành vi polymorphism và binary linkage.

Tạo **`BridgeDispatch.java`**:

```java
public class BridgeDispatch {

    static class Box<T> {
        T value() {
            return null;
        }
    }

    static class TextBox extends Box<String> {
        @Override
        String value() {
            return "hello";
        }
    }

    public static void main(String[] args) {
        Box<String> box = new TextBox();
        System.out.println("value=" + box.value());
    }
}
```

```bash
javac --release 17 BridgeDispatch.java
java BridgeDispatch
javap -c -v -p 'BridgeDispatch$TextBox'
```

**Stdout**:

```text
value=hello
```

Trong bytecode của `Box<T>`, method sau erasure có descriptor `value:()Ljava/lang/Object;`. `TextBox` khai báo method trả `String` với descriptor `value:()Ljava/lang/String;`. `javac` thường tạo thêm **bridge** trong `TextBox` có descriptor trả `Object`; bridge gọi method trả `String` thực tế.

Vì vậy, một lời gọi có symbolic reference đến `Box.value:()Ljava/lang/Object;` vẫn có thể dispatch tới **bridge `TextBox.value:()Ljava/lang/Object;`**, sau đó bridge chuyển tới body `TextBox.value:()Ljava/lang/String;`. Từ đó hiểu vì sao đôi khi `javap -p` thấy hai method trùng tên dù source chỉ khai báo một method.

Đừng xóa bridge method khỏi bytecode chỉ vì nó `synthetic`: các caller đã biên dịch có thể cần method đó để giữ hành vi gọi tương thích.

## 11. Những lỗi runtime liên quan tới resolution/selection

JVM không được phép chọn method bất kỳ nếu tham chiếu không hợp lệ. Phân biệt một số nhóm lỗi:

| Lỗi thường gặp | Tình huống điển hình | Ý nghĩa khi debug |
| --- | --- | --- |
| `NoSuchMethodError` | Caller đã biên dịch đối với descriptor/method tồn tại, nhưng thư viện runtime không còn method phù hợp | Kiểm tra **phiên bản binary**, descriptor, classpath/module path |
| `IllegalAccessError` | Method/reference không còn truy cập được theo quy tắc liên kết | Kiểm tra visibility, package/module và binary compatibility |
| `IncompatibleClassChangeError` | Caller kỳ vọng static/instance/interface/class không khớp hoặc interface defaults xung đột | Kiểm tra sự thay đổi hình dạng API giữa compile và runtime |
| `AbstractMethodError` | Target chọn được nhưng trừu tượng, không có implementation cụ thể tương ứng | Kiểm tra thay đổi interface/class library và class files |
| `NullPointerException` | Lời gọi instance dùng receiver `null` | Kiểm tra data flow của reference, không quy về thiếu method |
| `VerifyError` | Bytecode vi phạm ràng buộc typing/stack/control flow | Kiểm tra generator/instrumentation và verification metadata |
| `ExceptionInInitializerError` | Class initialization chạy `<clinit>` lỗi theo điều kiện chuẩn | Phân biệt lỗi khởi tạo class với lỗi tìm method |

Đây là các ví dụ **điển hình**, không phải “một lỗi chỉ có duy nhất nguyên nhân X”. Hãy xem stack trace, cause, Java version, class loader và bộ JAR đang chạy. Một test trên JDK mới/IDE có thể vượt qua trong khi production dùng lớp cùng tên từ phiên bản cũ hơn dẫn đến lỗi liên kết.

### Khi thay đổi thư viện, giữ đúng descriptor quan trọng hơn tên method

Ví dụ thư viện phiên bản 1 có `String lookup(int id)`, caller đã biên dịch chứa method reference với descriptor `(I)Ljava/lang/String;`. Nếu runtime được thay bằng thư viện phiên bản 2 **chỉ** có `String lookup(long id)`, hai method khác descriptor: caller cũ có thể bị `NoSuchMethodError`. Nếu chỉ đổi implementation override mà vẫn giữ contract tương thích, dispatch lại có thể hoạt động bình thường. Binary compatibility phải xét **class file và method descriptor**, không chỉ tên trong source.

## 12. Hiệu năng: dispatch động không đồng nghĩa “luôn tìm kiếm tuyến tính mỗi lần gọi”

JVM triển khai có thể dùng method tables, interface lookup caches, profiling hoặc JIT để giảm chi phí invocation. HotSpot JIT có thể **devirtualize** hoặc inline một lời gọi nếu có đủ chứng cứ về target; khi giả thiết không còn đúng, VM có cơ chế guard/deoptimization thích hợp để bảo toàn ngữ nghĩa.

Những phát biểu cần phân tầng:

- **Đặc tả JVMS bảo đảm:** chọn implementation phù hợp theo resolution/selection, ném lỗi tương ứng và giữ kết quả quan sát được.
- **Chi tiết triển khai:** cách HotSpot tra cứu target trong memory, số lần cache lookup, monomorphic/polymorphic call site, thời điểm inline và hình thức machine code.
- **Phép đo thực tế:** benchmark trên đúng JDK build, compiler, flags, workload và trạng thái warm-up.

Không kết luận “virtual luôn chậm hơn static một mức cố định”, “JVM mỗi lần gọi đều đi từng superclass”, hoặc “JIT bỏ được dynamic dispatch nên không còn kiểm tra kiểu” chỉ từ một đoạn `javap`. `javap` cho biết **bytecode invocation**, không hiển thị đầy đủ machine code tối ưu lúc runtime.

## 13. Quy trình đọc và phân tích một lời gọi method

Khi thấy `receiver.m(args)` hoặc một opcode `invoke*`, áp dụng lần lượt:

1. **Chốt Java version và class file đang chạy**: `java -version`, `javac -version`, phiên bản thư viện, runtime classpath/module path.
2. **Ở Java source, xác định kiểu tĩnh** của receiver/arguments và overload đã chọn theo JLS `15.12`. Ghi rõ cast/generics/autoboxing/varargs nếu có.
3. **Dùng `javap -c -v -p`**, tìm class method đang chứa call site và instruction `invokevirtual`/`invokeinterface`/`invokestatic`/`invokespecial`/`invokedynamic`.
4. **Đọc constant-pool entry** của instruction: target owner, tên method và JVM descriptor. Nhớ kiểu trả về cũng nằm trong descriptor.
5. **Xác định loại invocation**: virtual/interface cần selection theo receiver; static/special/dynamic có cơ chế khác.
6. **Xác định class runtime thực** của receiver ở call site, không suy diễn từ tên biến. Có thể quan sát hợp lệ qua `obj.getClass()` khi reference không null hoặc qua debugger.
7. **Đi theo override chain**: class runtime, superclass, interface defaults theo JVMS và quyền truy cập. Với generics xem thêm bridge method.
8. **Đọc frame/operand stack nếu cần**: receiver/args được truyền, method có phải instance/static, và kết quả trở lại caller bằng opcode return nào.
9. **Nếu lỗi liên kết**, kiểm tra binary khác phiên bản/loader trước khi kết luận JVM dispatch sai.
10. **Nếu thắc mắc hiệu năng**, tách contract khỏi JIT optimizations; dùng profiling/benchmark, không suy diễn từ `javap`.

### Tự kiểm tra

**Câu 1:** `Parent p = new Child(); p.run();`, `Child` override `run` hợp lệ. Method nào thực thi khi dùng invocation virtual?  
**Đáp:** `Child.run()`, do receiver runtime là `Child`.

**Câu 2:** Hai overload `f(Parent)` và `f(Child)`; gọi `f(p)` với `p` khai báo `Parent` nhưng chứa object Child. Overload nào được chọn?  
**Đáp:** `f(Parent)`, do compiler xét kiểu tĩnh.

**Câu 3:** `Parent p = new Child(); p.staticMethod();` nếu hai class cùng khai báo static method?  
**Đáp:** Phiên bản static thuộc `Parent` được chọn theo kiểu tĩnh; cú pháp gọi qua instance nên tránh trong code thực tế.

**Câu 4:** `Child.callSuper()` thực thi `super.run()`; có chọn `Child.run()` vì object vẫn là Child?  
**Đáp:** Không; `super` yêu cầu lời gọi đặc biệt tới implementation thích hợp của superclass.

**Câu 5:** Một interface default method bị override trong class concrete, biến receiver khai báo kiểu interface. Method nào chạy?  
**Đáp:** Implementation override trong class runtime, theo các quy tắc selection thích hợp.

**Câu 6:** Vì sao `javap` có thể thấy method bridge mà source không khai báo?  
**Đáp:** Compiler tạo method synthetic/bridge để duy trì hợp đồng polymorphism sau erasure/covariant return.

**Câu 7:** Constant pool hiện `Animal.sound`, nhưng output của object Dog lại là `Dog`; có lỗi không?  
**Đáp:** Không. Tham chiếu ký hiệu được resolve riêng; `invokevirtual` chọn implementation theo receiver runtime.

## 14. Những hiểu lầm thường gặp

| Hiểu lầm | Cách hiểu đúng |
| --- | --- |
| “Resolution chọn luôn method thực thi cuối cùng” | Với `invokevirtual`/`invokeinterface` còn có **selection** dựa vào object runtime |
| “Overload được quyết định khi JVM thấy object” | Overload source được compiler chọn theo kiểu tĩnh và quy tắc method invocation |
| “Kiểu tĩnh của biến buộc lời gọi luôn chạy ở class cha” | Kiểu tĩnh ràng buộc lời gọi hợp lệ; override có thể chạy theo kiểu runtime |
| “Đối tượng Dog có `bark()` nên biến Animal gọi `bark()` được” | Compiler vẫn yêu cầu member hợp lệ trên kiểu tĩnh hoặc cần cast hợp lệ |
| “Method static được override giống instance method” | Static method có **hiding**, không virtual dispatch |
| “Method private cùng tên ở lớp con override private của cha” | Private superclass method không được override theo cách đó |
| “`super.m()` vẫn chọn override ở receiver class con” | `super` dùng `invokespecial` theo quy tắc lookup đặc biệt |
| “Interface luôn chạy method body định nghĩa trên interface” | Class override có thể được chọn; default method chỉ là một khả năng |
| “Một lời gọi Java bằng một opcode `invokevirtual` trong mọi trường hợp” | Có `invokeinterface`, `invokestatic`, `invokespecial`, `invokedynamic` và compiler transformations |
| “Method bridge là code thừa có thể xóa” | Bridge giữ khả năng gọi đúng sau erasure/covariant return |
| “`javap` nói `Animal.sound` nên JVM không thể gọi Dog” | Constant pool chứa method **tham chiếu**; selection có thể tìm `Dog.sound` |
| “JVM luôn đi từng lớp cha lúc mỗi lần gọi” | Đặc tả quy định kết quả, implementation/JIT có thể tối ưu lookup và inline |

## Tài liệu gốc Java SE 17

- [JLS `15.12` — Method Invocation Expressions](https://docs.oracle.com/javase/specs/jls/se17/html/jls-15.html#jls-15.12): method lookup, overload applicability, most-specific method và runtime invocation.
- [JLS `8.4.8.1` — Overriding (by Instance Methods)](https://docs.oracle.com/javase/specs/jls/se17/html/jls-8.html#jls-8.4.8.1): điều kiện overriding.
- [JLS `8.4.8.2` — Hiding (by Class Methods)](https://docs.oracle.com/javase/specs/jls/se17/html/jls-8.html#jls-8.4.8.2): method static hiding.
- [JVMS `5.1` — The Run-Time Constant Pool](https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-5.html#jvms-5.1): symbolic method references.
- [JVMS `5.4.3.3–`5.4.3.4 — Method / Interface Method Resolution](https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-5.html#jvms-5.4.3.3): tìm symbol và ràng buộc liên kết.
- [JVMS `5.4.5 — Method Overriding](https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-5.html#jvms-5.4.5) và [`5.4.6 — Method Selection](https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-5.html#jvms-5.4.6): quan hệ override và cách chọn target dựa trên kiểu runtime.
- [JVMS `6.5` — Instruction Set](https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-6.html#jvms-6.5): `invokevirtual`, `invokeinterface`, `invokespecial`, `invokestatic`, `invokedynamic`.
- [JVMS `2.6` — Frames](https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-2.html#jvms-2.6): operand stack, local variables và frame khi gọi method.
- [JVMS `4.6 — Methods](https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-4.html#jvms-4.6) và [`4.3.3 — Method Descriptors](https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-4.html#jvms-4.3.3): tên/descriptor và method flags trong class file.
- [Java SE 17 `javap`](https://docs.oracle.com/en/java/javase/17/docs/specs/man/javap.html): đọc symbolic references và disassembly.

**Ghi nhớ:** **JLS overload** chọn lời gọi dựa vào kiểu tĩnh; **JVM resolution** phân giải method reference trong constant pool; **method selection** của `invokevirtual`/`invokeinterface` chọn implementation dựa trên kiểu runtime của receiver và quan hệ override hợp lệ. Ba tầng này giải thích vì sao cùng một object có thể cho kết quả khác nhau khi gọi overload, override, static hoặc `super`.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="stack-frames-and-calls-005">invokedynamic, BootstrapMethods và liên kết điểm gọi động</a>

<details>
<summary>Xem chi tiết</summary>

# invokedynamic, BootstrapMethods và liên kết điểm gọi động trong JVM

## Mục tiêu

Các lệnh gọi phương thức thường gặp trong bytecode Java như `invokestatic`, `invokevirtual`, `invokeinterface` và `invokespecial` có quy tắc liên kết dựa vào tham chiếu lớp/phương thức và kiểu gọi. **`invokedynamic`** bổ sung một cơ chế quan trọng: trình biên dịch cung cấp **mô tả điểm gọi** cùng một **bootstrap method** để JVM xác định *cách thức liên kết lời gọi* khi điểm gọi được sử dụng. Đây là nền tảng của nhiều đặc tính JVM hiện đại, đặc biệt là lambda và cách nối chuỗi được `javac` hiện đại sử dụng.

Sau bài, bạn có thể:
- Giải thích `invokedynamic` ở cấp instruction, operand stack và method descriptor.
- Lần theo `CONSTANT_InvokeDynamic` → `BootstrapMethods` → `MethodHandle` → `CallSite`.
- Phân biệt **bootstrap arguments tĩnh** trong class file với **arguments động** được đưa lên operand stack khi lời gọi chạy.
- Mô tả vòng đời của điểm gọi: chưa liên kết → bootstrap/link → lời gọi vào target, gồm lỗi bootstrap và các trường hợp race.
- Phân biệt `ConstantCallSite`, `MutableCallSite`, `VolatileCallSite`; nhận biết giới hạn visibility khi đổi target.
- Dùng Java 17+ biên dịch lambda/nối chuỗi, quan sát `javap -v -p` và hiểu tại sao index của constant pool không cố định.
- Phân biệt `CONSTANT_InvokeDynamic` với `CONSTANT_Dynamic` (condy) và tránh những nhận định sai như “`invokedynamic` gọi bootstrap mọi lần”.

## 1. Vì sao JVM cần một cơ chế gọi động?

Một ví dụ quen thuộc:

~~~java
object.toString();
~~~

Với lời gọi method instance thông thường, bytecode có thể mang `invokevirtual` với tham chiếu đến method được xác định bởi owner class, tên và method descriptor trong constant pool. JVM có quy tắc riêng để resolve phương thức và chọn implementation khi thực thi (ví dụ virtual dispatch).

Cơ chế trên không thuận tiện cho mọi yêu cầu của compiler/runtime: một call site có thể cần được **cấu hình bằng quy tắc riêng** mà compiler chỉ mô tả qua metadata. Đó là nơi `invokedynamic` có ích. Thay vì bytecode cố định một owner method theo mô hình gọi thông thường, nó chỉ đến **call site specifier**; JVM dùng **bootstrap method** do class file quy định để lấy một `CallSite` và method handle tương ứng.

Sơ đồ:

~~~text
Bytecode của caller
    |
    | invokedynamic #N
    v
CONSTANT_InvokeDynamic #N
    |
    +-- name_and_type_index --> tên điểm gọi + METHOD descriptor
    |
    +-- bootstrap_method_attr_index (bắt đầu từ 0)
            |
            v
        BootstrapMethods attribute
            |
            +-- bootstrap_method_ref --> CONSTANT_MethodHandle
            +-- bootstrap_arguments[] --> các entry constant pool
            |
            v
        Bootstrap Method được JVM gọi để liên kết
            |
            v
        CallSite có target là MethodHandle
            |
            v
        target được gọi bằng dynamic args trên operand stack
~~~

Sơ đồ này mô tả mối quan hệ logic, **không phải layout cố định trong bộ nhớ**. Những mục `#N` là chỉ số class file cụ thể; thứ tự, số index và một số tên do compiler quyết định. `invokedynamic` không có nghĩa mỗi lần chạy sẽ tìm lại phương thức bằng cách phân giải tên một cách chậm chạp.

### “Dynamic” nằm ở giai đoạn nào?

Có hai giai đoạn riêng:
1. **Liên kết điểm gọi (*linkage*)**: bootstrap được kích hoạt theo quy tắc resolution cho một instruction `invokedynamic` chưa liên kết, chuẩn bị một `CallSite` hợp lệ.
2. **Thực thi lời gọi (*invocation*)**: mỗi lần đi qua instruction, các arguments runtime được lấy từ operand stack để gọi target hiện hành của call site.

Cơ chế liên kết có thể chọn target một lần hoặc cung cấp `CallSite` có target thay đổi được, tùy loại call site và chương trình. Điều này khác với việc bootstrap cứ chạy lại trước từng invocation.

## 2. Instruction `invokedynamic` và operand stack

`invokedynamic` có opcode thập phân **186**, hệ 16 là **`0xBA`**. Định dạng trong class file:

~~~text
invokedynamic indexbyte1 indexbyte2 0 0

index = (indexbyte1 << 8) | indexbyte2
~~~

Hai byte chỉ số tạo thành operand không dấu 16 bit trỏ tới một **`CONSTANT_InvokeDynamic`** trong run-time constant pool của class hiện hành; **hai byte sau phải bằng 0** theo định dạng JVM. Khi xem bằng `javap -c`, bạn thường thấy:

~~~text
invokedynamic #N,  0  // InvokeDynamic #k:applyAsInt:(I)Ljava/util/function/IntUnaryOperator;
~~~

Đây chỉ là **minh họa dạng hiển thị**: tên, descriptor và chỉ số phụ thuộc class đã biên dịch. `#N` sau opcode và `#k` trong chú thích có ý nghĩa khác: `#N` là constant pool index, còn `#k` thường thể hiện bootstrap method table index do `javap` định dạng.

**Method descriptor** ở `NameAndType` xác định kiểu tham số và kiểu trả về của *lời gọi tại chính điểm đó*. Ví dụ:

~~~text
(II)I                         // hai int -> int
(Ljava/lang/String;)V         // String -> void
(I)Ljava/lang/String;        // int -> String
()Ljava/lang/Runnable;       // không tham số -> Runnable
(I)Ljava/util/function/IntUnaryOperator; // int -> IntUnaryOperator
~~~

Với descriptor `(II)I`, trước lệnh operand stack có hai `int`:

~~~text
Stack trước:   ..., int1, int2
invokedynamic: lấy cả hai đối số, gọi target phù hợp
Stack sau:     ..., intResult
~~~

Nếu descriptor trả về `V` (void), không push giá trị trả về. Với `long`/`double`, JVM sử dụng category-2 value trên stack. Những tham số đã được đưa lên stack chính là **dynamic arguments**; chúng **khác** với bootstrap static arguments trong attribute.

Khác các lời gọi instance như `invokevirtual`, instruction `invokedynamic` không có một operand `objectref` ngầm định. Nếu muốn truyền receiver vào call target, receiver đó có thể là **tham số hiện rõ trong descriptor**, tức một dynamic argument thông thường.

## 3. Class file: `CONSTANT_InvokeDynamic`

Theo JVMS, entry có tag **18**:

~~~text
CONSTANT_InvokeDynamic_info {
    u1 tag;                            // 18
    u2 bootstrap_method_attr_index;
    u2 name_and_type_index;
}
~~~

Trong đó:
- **`bootstrap_method_attr_index`** trỏ đến **một phần tử trong bảng `bootstrap_methods[]`** thuộc attribute `BootstrapMethods` của class file. Chỉ số này **bắt đầu từ 0**, không phải chỉ mục constant pool.
- **`name_and_type_index`** trỏ đến `CONSTANT_NameAndType`; từ đây có thể đọc **unqualified name** và **method descriptor** của call site. Đây là một *method descriptor*, không phải field descriptor.
- `CONSTANT_InvokeDynamic` **không có `class_index`** kiểu `CONSTANT_Methodref`. Việc chọn method handle target là kết quả của bootstrap, không phải một phép resolve cố định trực tiếp theo owner/method của caller.
- Class file sử dụng các loại entry này phải tuân thủ yêu cầu phiên bản, đặc tả constant pool và thuộc tính bootstrap. `invokedynamic`/`CONSTANT_InvokeDynamic` có từ Java 7, tương ứng major class file 51 trở lên.

Đừng lấy tên call site làm một phương thức Java chắc chắn tồn tại. Tên được truyền tới bootstrap làm metadata để bootstrap diễn giải; **không nhất thiết có một method cùng tên được khai báo ở class owner nào đó**.

### Cách nhận biết dạng `NameAndType`

Giả sử `javap -v` cho ra những mục minh họa:

~~~text
#20 = InvokeDynamic      #0:#21    // #0:applyAsInt:(I)Ljava/util/function/IntUnaryOperator;
#21 = NameAndType        #22:#23   // applyAsInt:(I)Ljava/util/function/IntUnaryOperator;
#22 = Utf8               applyAsInt
#23 = Utf8               (I)Ljava/util/function/IntUnaryOperator;
~~~

Đây là **một đoạn giả lập để giải thích**, không phải output có thể dự đoán đúng từ mọi phiên bản compiler. Đọc theo chuỗi:
1. Entry `#20` có tag InvokeDynamic.
2. Số `#0` ở bootstrap portion chỉ phần tử **0** của attribute `BootstrapMethods`.
3. `#21` là `NameAndType` trong constant pool; từ đó mới đọc tên và descriptor.
4. Để biết phương thức bootstrap thực tế, xem **attribute `BootstrapMethods`**, không coi `#0` là `constant_pool[0]`.

## 4. Attribute `BootstrapMethods` chứa gì?

Attribute `BootstrapMethods` nằm ở **cấp class** trong `ClassFile`. Nếu constant pool có một hoặc nhiều `CONSTANT_InvokeDynamic` hoặc `CONSTANT_Dynamic`, class file phải có đúng **một** attribute `BootstrapMethods` theo quy tắc định dạng.

Sơ đồ rút gọn:

~~~text
BootstrapMethods_attribute {
    u2 attribute_name_index; // Utf8 "BootstrapMethods"
    u4 attribute_length;
    u2 num_bootstrap_methods;
    {
        u2 bootstrap_method_ref;
        u2 num_bootstrap_arguments;
        u2 bootstrap_arguments[num_bootstrap_arguments];
    } bootstrap_methods[num_bootstrap_methods];
}
~~~

Mỗi phần tử bootstrap table gồm:
- **`bootstrap_method_ref`**: chỉ mục constant pool tới một **`CONSTANT_MethodHandle`**, mô tả bootstrap method.
- **`bootstrap_arguments[]`**: dãy chỉ mục constant pool cho các **static bootstrap arguments** được phép. Từng argument được resolve/diễn giải theo loại hằng của nó.
- Bảng được đánh chỉ số **từ 0**; trong khi constant pool trong class file đánh chỉ số **từ 1** (vị trí 0 không dùng). Đây là lỗi đọc class file rất phổ biến.

Một bản `javap -v` có thể trình bày:

~~~text
BootstrapMethods:
  0: #40 REF_invokeStatic java/lang/invoke/LambdaMetafactory.metafactory:...
    Method arguments:
      #47 (I)I
      #48 REF_invokeStatic Example.lambda$main$0:(II)I
      #47 (I)I
~~~

Các index/names ở trên chỉ nhằm **minh họa quan hệ bootstrap**; tên method synthetic và kiểu có thể khác. Điểm quan trọng là **method handle cho bootstrap** ở cùng bảng với **các hằng bổ sung** như `MethodType` hoặc `MethodHandle`. Những arguments này hỗ trợ bootstrap tạo một target khớp descriptor của call site.

### Hai nhóm arguments không được nhầm lẫn

| Nhóm | Nguồn | Có ở thời điểm nào? | Ví dụ |
| --- | --- | --- | --- |
| **Bootstrap static arguments** | `bootstrap_arguments[]` trỏ vào constant pool | Khi bootstrap/link point được xử lý | MethodType của SAM, MethodHandle của lambda body, recipe nối chuỗi |
| **Bootstrap JVM-supplied arguments** | JVM cung cấp khi gọi bootstrap | Khi resolution call site | `MethodHandles.Lookup`, call site name, `MethodType` của lời gọi |
| **Dynamic invocation arguments** | Operand stack tại `invokedynamic` | **Mỗi lần** instruction được thực thi | Giá trị captured từ scope, số để nối chuỗi, receiver theo descriptor |

Các bootstrap arguments **không tự xuất hiện trên operand stack của chương trình caller**. Ngược lại, các dynamic arguments **không phải metadata trong BootstrapMethods**: chúng biến đổi giữa các lần chạy lệnh.

## 5. Vòng đời liên kết (*linkage*) của một call site

Quy trình khái quát khi JVM lần đầu thực thi một instruction `invokedynamic` chưa liên kết:

1. **Đọc symbolic reference** `CONSTANT_InvokeDynamic` của instruction, gồm tên/descriptor và bootstrap table index.
2. **Resolve các hằng cần thiết** như `MethodHandle` của bootstrap và static arguments, xác định `MethodType` của call site.
3. **Chuẩn bị đối số cho bootstrap**. Trong trường hợp bootstrap điển hình, ba đối số đầu gồm `MethodHandles.Lookup` với quyền của caller, tên call site kiểu `String`, `MethodType` của call site; tiếp theo là các static arguments đã được định nghĩa. Signature thực tế phải tương thích với quy tắc gọi bootstrap.
4. **Gọi bootstrap method** được chỉ định bằng method handle. Bootstrap quyết định cách tạo target và trả một đối tượng **`CallSite`** thích hợp.
5. **Kiểm tra kết quả**: không null, là `CallSite` và **type của CallSite tương thích đúng với method descriptor được chỉ định**. Nếu không thỏa, quá trình resolution thất bại.
6. **Gắn kết quả với instruction call site đó**. Những lần thực thi tiếp theo của **chính instruction ấy** sử dụng kết quả đã được liên kết để gọi target tương ứng (có thể thay đổi nếu là loại CallSite cho phép).

~~~text
Lần đầu instruction indy chạy
   |
   v
Kiểm tra call site đã liên kết?
   |
   +-- CHƯA --> resolve bootstrap metadata
   |                |
   |                v
   |          gọi bootstrap method
   |                |
   |                v
   |          validate CallSite + type
   |                |
   |                v
   |          gắn kết quả cho instruction
   |
   +-- RỒI ------+
                 |
                 v
       đọc target của CallSite theo semantics loại site
                 |
                 v
       truyền dynamic args -> gọi MethodHandle
                 |
                 v
       trả kết quả lên operand stack nếu không void
~~~

**Không khẳng định bootstrap luôn chạy chính xác một lần vật lý.** JVMS cho phép những thread khác nhau có thể cùng cố resolve một site chưa liên kết; bootstrap có thể được kích hoạt bởi hơn một thread trong cuộc đua, nhưng **chỉ một kết quả được chọn làm liên kết chính thức**. Do đó bootstrap nên tránh side effect không idempotent hoặc phải xử lý được việc gọi cạnh tranh. Khi site đã liên kết thành công, các lần thực thi tiếp theo của *instruction đó* không liên kết lại như một site mới.

### Điểm gọi nào được cache?

Cần phân biệt **một entry constant pool** và **một occurrence của instruction `invokedynamic`**. Quy tắc resolution của indy gắn liên kết với **chính occurrence instruction**; hai instruction `invokedynamic` khác nhau có thể tham chiếu cùng một symbolic entry nhưng vẫn có kết quả resolution riêng. Vì vậy không thể suy luận “cùng `#N` thì hai vị trí bytecode phải sử dụng cùng một `CallSite`”.

Tương tự, việc gọi một method Java chứa instruction `invokedynamic` nhiều lần **không tạo call site mới theo từng lần gọi method**. Call site thuộc vị trí instruction trong class hiện hành, không phải từng frame stack đang hoạt động.

### Frame và method invocation

Liên kết một call site là hoạt động của JVM ở cấp runtime, nhưng khi target được gọi, **method handle invocation** vẫn phải tuân thủ kiểu tham số/trả về và các quy tắc invocation. Không tồn tại một “frame BootstrapMethods” cố định cho mọi lần gọi: bootstrap được gọi ở giai đoạn link, còn target được gọi ở giai đoạn thực thi instruction. JVM/JIT có thể tối ưu implementation thực tế, nhưng semantics quan sát được phải phù hợp với liên kết và target hiện hành của `CallSite`.

## 6. `CallSite` và `MethodHandle`

**`MethodHandle`** là một giá trị runtime có kiểu `MethodType`, biểu diễn khả năng gọi một mục tiêu theo cơ chế được định nghĩa. **`CallSite`** là đối tượng nắm giữ một **target MethodHandle** của một điểm gọi động.

Ba nhóm CallSite thường gặp:

| Loại | Target sau khi tạo | Điều cần chú ý |
| --- | --- | --- |
| **`ConstantCallSite`** | Không đổi sau khởi tạo hợp lệ | Phù hợp khi target của điểm gọi đã cố định; dễ tối ưu. |
| **`MutableCallSite`** | Có thể thay đổi bằng `setTarget` | Việc quan sát target mới ở các thread khác có quy tắc visibility riêng; `MutableCallSite.syncAll` là công cụ đồng bộ hóa thay đổi. |
| **`VolatileCallSite`** | Có thể thay đổi, đọc/ghi target có semantics tương tự volatile | Hữu ích khi cần visibility mạnh hơn mà không muốn tự quản lý đồng bộ site mutable. |

**`MethodHandle` không tự đồng nghĩa `Methodref` trong constant pool.** `CONSTANT_MethodHandle` là *mô tả nhị phân* để tạo/resolve một method handle; `java.lang.invoke.MethodHandle` là *đối tượng runtime*. Bootstrap có thể kết hợp/adapt các handle để tạo target, và `CallSite` nắm target sẽ được gọi.

**Loại của target quan trọng:** bootstrap phải trả một `CallSite` có `type()` khớp với call site descriptor. Nếu không khớp, không thể lấy một target có tham số/trả về bất kỳ rồi mặc kệ để JVM “tự đoán”; bootstrap phải dùng các combinator như `asType`, `bindTo`, `insertArguments` hoặc dựng handle đúng kiểu khi cần.

### Thay target và tính hiển thị giữa các thread

Với `MutableCallSite`, `setTarget` thay target, nhưng việc nhiều thread cùng thực thi có thể tiếp tục thấy target cũ nếu không thiết lập visibility thích hợp. `syncAll` giúp đồng bộ thay đổi theo contract của `MutableCallSite`, nhưng không phải cơ chế transaction của toàn ứng dụng. Với `VolatileCallSite`, target có semantics tương tự thao tác volatile cho việc đọc/ghi target; chi phí và nhu cầu đồng bộ khác nhau tùy trường hợp.

**CallSite không có nghĩa một method body Java bị “ghi đè” hoặc “chỉnh sửa” khi chạy.** Nó chọn *method handle target* cho vị trí gọi, có thể chuyển từ behavior A sang B, trong khi class/method bytecode không nhất thiết bị sửa.

## 7. Ví dụ chạy được Java 17+: lambda và nối chuỗi

Lập trình viên không có cú pháp Java nguồn tương đương một cách trực tiếp với instruction `invokedynamic`. Thay vào đó, **compiler** phát instruction khi cần. Hai ví dụ phổ biến của `javac` hiện đại là **lambda expression** và **string concatenation**.

Tạo tệp **`InvokeDynamicDemo.java`**:

~~~java
import java.util.function.IntUnaryOperator;

public class InvokeDynamicDemo {
    private static String message(int value) {
        return "value=" + value;
    }

    public static void main(String[] args) {
        int offset = 3;
        IntUnaryOperator addOffset = n -> n + offset;

        System.out.println("lambda=" + addOffset.applyAsInt(7));
        System.out.println("concat=" + message(42));
    }
}
~~~

Biên dịch và chạy với **JDK 17+**:

~~~bash
javac --release 17 InvokeDynamicDemo.java
java InvokeDynamicDemo
~~~

**Stdout chính xác:**

~~~text
lambda=10
concat=value=42
~~~

Phân tích:
- Biến `offset = 3` được lambda capture. Lambda `n -> n + offset` sau khi chuyển thành một implementation của `IntUnaryOperator` cộng `n` với 3; `applyAsInt(7)` trả 10.
- Biểu thức nối chuỗi `"value=" + value` trả `"value=42"`.
- `System.out.println` là lời gọi API Java bình thường; không phải bản thân từ `println` là một opcode indy.

Quan sát bytecode:

~~~bash
javap -c -p InvokeDynamicDemo
javap -v -p InvokeDynamicDemo
~~~

Trong **`javac --release 17` thông thường**:
- `main` có một `invokedynamic` liên quan đến tạo instance functional interface cho lambda; bootstrap thường tham chiếu `java/lang/invoke/LambdaMetafactory.metafactory`.
- `message` có thể có một `invokedynamic` phục vụ nối chuỗi; bootstrap thường tham chiếu `java/lang/invoke/StringConcatFactory.makeConcatWithConstants`.
- Có thể thấy một method synthetic dạng `lambda$main$0` được compiler tạo để chứa thân lambda. **Không được phụ thuộc chính xác vào tên/đánh số synthetic method**, thứ tự hoặc chỉ mục `#N`.
- Những biểu thức ghép chuỗi khác trong `main` cũng có thể tạo các indy call site. Các chỉ mục constant pool/recipe khác nhau theo cách compiler sinh code.

Ví dụ một dạng disassembly **rút gọn, chỉ để nhận diện**:

~~~text
invokedynamic #N, 0  // InvokeDynamic #k:applyAsInt:(I)Ljava/util/function/IntUnaryOperator;
...
invokeinterface #M   // InterfaceMethod IntUnaryOperator.applyAsInt:(I)I
...
invokedynamic #P, 0  // InvokeDynamic #j:makeConcatWithConstants:(I)Ljava/lang/String;
~~~

**Phân biệt hai invocation!** `invokedynamic` trong trường hợp lambda thường **tạo đối tượng functional interface ở call site**; việc gọi `addOffset.applyAsInt(7)` sau đó có thể sử dụng `invokeinterface`, không phải mọi lần gọi lambda đều thực thi cùng opcode indy. Bootstrap có nhiệm vụ tạo linkage/factory hành vi; call site của việc *tạo lambda object* là một lớp nghĩa riêng với method invocation trên object đó.

## 8. Đi sâu vào LambdaMetafactory

Với lambda hoặc method reference, `javac` thường sử dụng `LambdaMetafactory` để tạo một factory cho implementation functional interface. Bộ thông tin điển hình gồm:

1. **Lookup:** bối cảnh truy cập của class caller.
2. **Invoked name:** thường liên quan tên abstract method của functional interface, ví dụ `applyAsInt`.
3. **Invoked type:** descriptor tại vị trí call site, trả về functional interface; tham số có thể là các captured values, ví dụ `(I)Ljava/util/function/IntUnaryOperator;`.
4. **SAM method type:** chữ ký abstract method mà functional interface yêu cầu, ví dụ `(I)I`.
5. **Implementation method handle:** tham chiếu đến lambda body synthetic/static/instance method hoặc một method reference phù hợp.
6. **Instantiated method type:** method type sau khi áp dụng generic adaptation/instantiation của lambda.

Từ đó bootstrap có thể sản xuất một `ConstantCallSite` mà target là **factory method handle**, có chức năng tạo một object tương thích functional interface. Khi Java chạy `n -> n + offset`, giá trị capture thường được truyền như **dynamic argument** vào factory của call site. Khi gọi `applyAsInt` trên instance kết quả, implementation được sinh sẽ chuyển tiếp tới logic lambda body.

### Tính capture và “stateless”

Nếu lambda **không capture** biến nào, invoked type có thể có descriptor không tham số và có thể được triển khai bằng cách tái sử dụng một instance. Tuy nhiên, **không được phụ thuộc identity** (¤==¤) của hai lambda object từ những lần đánh giá khác nhau: đặc tả ngôn ngữ không hứa object mới mỗi lần hay một singleton ổn định.

Nếu lambda capture một giá trị, invoked type có thể mang các captured values làm tham số, ví dụ `(I)Ljava/util/function/IntUnaryOperator;`. Điều này không có nghĩa body của lambda chạy tại thời điểm tạo factory; tạo lambda instance và gọi method của instance là **hai hành động khác nhau**.

### Method reference không phải một API gọi động riêng

Biểu thức nguồn `String::length` hoặc `MyClass::staticMethod` cũng có thể được compiler triển khai qua `LambdaMetafactory`. Nhưng đây là lựa chọn **của compiler theo phiên bản/chiến lược**, không phải lời hứa mọi expression dạng `::` đều phát chính xác một lệnh indy. Quan sát bytecode thực tế thay vì gán cứng số lượng opcode.

## 9. Nối chuỗi qua StringConcatFactory

Kể từ Java 9, `javac` thường triển khai toán tử nối chuỗi `+` qua `invokedynamic` với một bootstrap `StringConcatFactory`, thay vì luôn dựng một chuỗi `new StringBuilder().append(...).toString()` trực tiếp tại mỗi call site như những bytecode cũ.

Ví dụ:

~~~java
static String describe(String name, int age) {
    return "name=" + name + ", age=" + age;
}
~~~

Đây là **method minh họa** cần đặt trong một class; có thể biên dịch trong ví dụ chương trình đầy đủ nếu muốn. Với target Java 17, compiler thường dùng một call site có invoked type tương ứng với các biến động, kiểu như:

~~~text
(Ljava/lang/String;I)Ljava/lang/String;
~~~

Bootstrap `StringConcatFactory.makeConcatWithConstants` có thể được cung cấp một **recipe string** trong các static arguments, trong đó một số ký tự đánh dấu vị trí dynamic arguments hoặc static constants. **Recipe là metadata bootstrap**, không phải dynamic input truyền từ stack mỗi lần gọi.

Khi runtime chạy expression với `name="An"`, `age=20`, các giá trị `"An"` và `20` mới là dynamic arguments được đặt lên stack rồi đi qua call site để tạo chuỗi kết quả. Cùng call site có thể chạy tiếp với `name="Binh"`, `age=21` mà **không cần bootstrap lại để biết một method nào sẽ được dùng**.

### Có phải kết quả String cũng là CallSite?

Không. **CallSite** là đối tượng liên kết cho instruction. **String** là kết quả của việc target xử lý các arguments tại runtime. Trộn hai khái niệm này dễ dẫn đến lập luận sai như “mỗi lần nối chuỗi tạo một CallSite” hoặc “BootstrapMethods chứa từng chuỗi kết quả”.

## 10. Ví dụ Java API độc lập: đổi target của MutableCallSite

Để hiểu CallSite tốt hơn, ta có thể thao tác `java.lang.invoke` trực tiếp bằng Java chuẩn. **Ví dụ dưới đây không tự phát opcode `invokedynamic` tùy biến.** Nó sử dụng public API để cho thấy target của một `CallSite` có thể được thay đổi. Vì mã nguồn Java không có toán tử gọi `invokedynamic` trực tiếp, bạn cần compiler hoặc một bộ sinh class file/bytecode khi muốn định nghĩa call site tùy biến trong bytecode.

Tạo tệp **`MutableSiteDemo.java`**:

~~~java
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.MutableCallSite;

public class MutableSiteDemo {
    private static String first(String name) {
        return "A:" + name;
    }

    private static String second(String name) {
        return "B:" + name;
    }

    public static void main(String[] args) throws Throwable {
        MethodHandles.Lookup lookup = MethodHandles.lookup();
        MethodType type = MethodType.methodType(String.class, String.class);

        MethodHandle initial = lookup.findStatic(
                MutableSiteDemo.class, "first", type);

        MutableCallSite site = new MutableCallSite(initial);
        MethodHandle invoker = site.dynamicInvoker();

        String before = (String) invoker.invokeExact("x");
        System.out.println("before=" + before);

        MethodHandle replacement = lookup.findStatic(
                MutableSiteDemo.class, "second", type);

        site.setTarget(replacement);
        MutableCallSite.syncAll(new MutableCallSite[] { site });

        String after = (String) invoker.invokeExact("x");
        System.out.println("after=" + after);
    }
}
~~~

Biên dịch và chạy Java 17+:

~~~bash
javac --release 17 MutableSiteDemo.java
java MutableSiteDemo
~~~

**Stdout chính xác:**

~~~text
before=A:x
after=B:x
~~~

Giải thích:
- `MethodType.methodType(String.class, String.class)` mô tả target nhận một `String` và trả một `String`.
- `findStatic` lấy method handle của `first`. `MutableCallSite` giữ target đó.
- `dynamicInvoker()` trả handle mà hành vi theo target hiện tại của site.
- `setTarget(replacement)` đổi target; `syncAll` thể hiện cách phát tín hiệu đồng bộ khi có nhiều thread quan sát site. Trong ví dụ single-thread, kết quả đầu/ cuối có thể xác định như đã in.
- `invokeExact("x")` đòi kiểu tham số và kiểu trả về chính xác theo `MethodType`; cast `(String)` ở call site giúp compiler phát chữ ký return phù hợp cho invocation.

**Không nhầm `CallSite` với `invokedynamic`:** API Java này minh họa đối tượng site và method handles. Lệnh gọi `MethodHandle.invokeExact` trong class file có quy tắc đặc biệt của `invokevirtual` signature-polymorphic; đó không phải cách chứng minh đã phát ra một instruction `invokedynamic` tùy biến. Với ví dụ lambda ở trên, bạn mới quan sát một trường hợp compiler thực sự phát indy.

## 11. Phân biệt invokedynamic và dynamic constant (condy)

Cả `CONSTANT_InvokeDynamic` và `CONSTANT_Dynamic` cùng dùng bootstrap table nhưng mục đích khác nhau.

| Tiêu chí | `CONSTANT_InvokeDynamic` | `CONSTANT_Dynamic` |
| --- | --- | --- |
| Constant pool tag | **18** | **17** |
| Dùng để | Mô tả **điểm gọi** cần liên kết | Mô tả **giá trị hằng** cần tính khi resolve |
| `NameAndType` descriptor | **Method descriptor** như `(I)Ljava/lang/String;` | **Field descriptor** như `Ljava/lang/String;` hoặc `J` |
| Cách sử dụng | Opcode **`invokedynamic`** | Một trong các lệnh nạp hằng `ldc`/`ldc_w`/`ldc2_w` theo kiểu và quy tắc |
| Kết quả bootstrap | Một **`CallSite`** tương thích descriptor | Một **giá trị** tương thích kiểu hằng được mô tả |
| Có mặt từ | Class file major **51** (Java 7) | Class file major **55** (Java 11) |

**BootstrapMethods** có thể chứa các entry phục vụ cả hai loại. Điều đó **không** làm hai loại trở thành một: indy là cơ chế gọi; condy là cơ chế tính hằng. Nếu thấy `Dynamic` trong `javap -v`, đừng tự động kết luận class có instruction `invokedynamic`.

### Các loại reference liên quan cũng khác nhau

| Loại constant pool | Công dụng |
| --- | --- |
| `CONSTANT_Methodref` | Symbolic reference cho method class trong quy tắc resolve thông thường |
| `CONSTANT_InterfaceMethodref` | Symbolic reference cho method interface |
| `CONSTANT_MethodHandle` | Mô tả method handle, gồm reference kind và index đến reference phù hợp |
| `CONSTANT_MethodType` | Mô tả một method type qua descriptor |
| `CONSTANT_InvokeDynamic` | Mô tả điểm gọi động, trỏ đến bootstrap index và `NameAndType` |
| `CONSTANT_Dynamic` | Mô tả hằng động, trỏ đến bootstrap index và `NameAndType` |

Tuy tên nhiều entry bắt đầu `CONSTANT_`, **constant pool không chỉ chứa giá trị literal**. Nó cũng chứa metadata giúp JVM liên kết code vào runtime.

## 12. Lỗi trong liên kết điểm gọi động

Khi bootstrap không thể chạy/không thể cho ra kết quả hợp lệ, JVM thường báo **`BootstrapMethodError`**, một dạng lỗi liên kết. Các trường hợp cần xem xét:

- Bootstrap method handle không thể resolve do lớp/entry sai, access hoặc binary không tương thích.
- Bootstrap ném exception, khởi tạo dependency thất bại, hoặc thực hiện logic lỗi.
- Bootstrap trả **null** hoặc một đối tượng không phải `CallSite` trong trường hợp indy.
- Bootstrap trả `CallSite` có type không khớp method descriptor của điểm gọi.
- `BootstrapMethods`/constant pool chứa index hoặc tag không hợp lệ, class file không thỏa format/verification constraints — một số tình huống có thể biểu hiện thành **`ClassFormatError`, `VerifyError` hoặc lỗi liên kết khác**, không nhất thiết đều được bọc thành `BootstrapMethodError`.

**Không chẩn đoán tất cả lỗi `BootstrapMethodError` là “thiếu class lambda”.** Hãy xem nguyên nhân gốc, call site name/type, bootstrap method handle, static arguments, class loading và phiên bản dependency liên quan.

### Khi bootstrap thất bại thì sao?

Sau khi JVM xác định resolution của một instruction indy **thất bại**, lần thử tiếp theo của **cùng instruction** không tự động gọi lại bootstrap để “thử vận may” theo mỗi invocation. Lỗi resolution được duy trì theo semantics đặc tả. Điều này quan trọng: bootstrap không phải cơ chế retry cho network, IO hoặc registry tạm thời bị lỗi. Nếu muốn runtime logic có thể thay đổi, nên liên kết thành công đến một `CallSite` có target thích hợp để chính target xử lý retry/fallback theo thiết kế.

### Vấn đề concurrency trong bootstrap

Nếu hai thread cùng chạm đến một điểm gọi chưa liên kết, JVM có thể cho phép bootstrap được chạy nhiều lần trong một cuộc đua. Mặc dù một kết quả được công nhận để liên kết instruction, **side effects xảy ra trong những bootstrap executions không được chọn có thể vẫn quan sát được**. Vì vậy bootstrap nên có logic thuần/ít side effect, hoặc được thiết kế idempotent và thread-safe khi cập nhật cache/registry.

### Đổi target không đúng kiểu

Với một `CallSite` đã liên kết, `setTarget` trên loại cho phép thay đổi vẫn phải tôn trọng type tương ứng; target không tương thích sẽ gây lỗi tại API. Không thể đổi từ handle `(int)int` sang `(String)String` cho cùng call site chỉ bằng cách gán tùy ý.

## 13. Cách đọc javap từng bước

Với `InvokeDynamicDemo.class`, làm theo các bước:

1. Chạy `javap -c -p InvokeDynamicDemo` để xác định các instruction `invokedynamic` ở phương thức nào và operand index `#N` là bao nhiêu.
2. Chạy `javap -v -p InvokeDynamicDemo`. Trong phần **Constant pool**, tìm `#N = InvokeDynamic #k:#m`.
3. Theo `#m` đến `NameAndType`, rồi xem **tên call site** và **method descriptor**. Descriptor cho biết dynamic arguments cần nằm trên operand stack.
4. Theo **`#k` trong chú thích/bảng** đến phần **BootstrapMethods** cuối output. Lưu ý index bootstrap bắt đầu từ 0.
5. Trong entry bootstrap, đọc `REF_invokeStatic ...LambdaMetafactory.metafactory` hoặc `...StringConcatFactory.makeConcatWithConstants` nếu dùng `javac` theo cấu hình ví dụ.
6. Theo các **Method arguments** là static bootstrap arguments như method handle, method type, recipe; không xem chúng là dữ liệu runtime truyền mỗi lần.
7. Đối chiếu bytecode chuẩn bị stack ngay trước instruction `invokedynamic`: các ¤iload`, ¤aload` hoặc opcode khác đưa dynamic arguments đúng descriptor.
8. Xem instruction **sau** indy: call site trả kết quả gì? Nếu trả functional interface, sẽ có đoạn code tiếp theo lưu/thực thi instance ấy, thường bằng `invokeinterface`.
9. Ghi rõ compiler, target class file và kết quả trong ghi chép; **không hardcode các chỉ số `#N`** giữa các lần biên dịch.

### Dạng bản đồ ở javap

~~~text
Code:
  ...
  invokedynamic #N, 0      // InvokeDynamic #k:someName:(I)Ljava/lang/Runnable;
  ...

Constant pool:
  #N = InvokeDynamic #k:#m  // #k:someName:(I)Ljava/lang/Runnable;
  #m = NameAndType   #a:#b
  #a = Utf8          someName
  #b = Utf8          (I)Ljava/lang/Runnable;
  ...

BootstrapMethods:
  k: #handle REF_invokeStatic some/bootstrap/Owner.bootstrap:...
    Method arguments:
      #arg1 ...
      #arg2 ...
~~~

Đây là **mẫu biểu diễn tổng quát**, không phải output exact của một class hay câu khẳng định bootstrap luôn nằm ở class tên `some.bootstrap.Owner`. Trong một bản dump, `javap` có thể in nhiều mức comment đã diễn giải giúp đọc dễ hơn.

## 14. Tình huống thực tế và lỗi tư duy phổ biến

| Nhận định/tình huống | Phân tích đúng |
| --- | --- |
| “`invokedynamic` là gọi method có tên lưu trong constant pool, giống `invokevirtual`.” | Indy liên kết qua bootstrap được chỉ định; call site name/type là metadata, target lấy từ `CallSite`. |
| “Bootstrap chạy trước mỗi lần lambda được gọi.” | Bootstrap phục vụ liên kết site; lambda object có thể được tạo qua linked site, sau đó SAM method được gọi bằng cơ chế khác. |
| “Một entry `InvokeDynamic` phải có một target chung cho mọi instruction tham chiếu.” | Linkage của call site gắn theo **occurrence instruction**. |
| “Bootstrap luôn chỉ chạy một lần vật lý.” | Những thread race có thể thực hiện nhiều bootstrap executions; chỉ một kết quả được chọn. |
| “BootstrapMethod index `#0` là entry constant pool số 0.” | Bootstrap table có chỉ số bắt đầu 0; constant pool bắt đầu 1. |
| “Dynamic arguments là Method arguments liệt kê trong `BootstrapMethods`.” | Method arguments ở attribute là **static bootstrap arguments**; dynamic invocation arguments nằm trên operand stack. |
| “`CallSite` là kết quả String của phép nối chuỗi.” | CallSite mô tả target đã liên kết; String là kết quả mỗi lần invocation. |
| “`MutableCallSite.setTarget` khiến mọi thread lập tức thấy target mới.” | Có quy tắc visibility; dùng `syncAll` hoặc `VolatileCallSite` khi phù hợp. |
| “Thấy `invokedynamic` nghĩa là JVM phải dùng reflection.” | Cơ chế chính dựa trên MethodHandle/CallSite, không đồng nghĩa `java.lang.reflect.Method.invoke`. |
| “`invokedynamic` luôn chậm do bootstrap.” | Bootstrap là chi phí liên kết site; sau đó JIT có thể tối ưu, nhưng hiệu năng phụ thuộc target, site kind và workload. |
| “Lambda lúc nào cũng tạo class file riêng trước runtime.” | Compiler/runtime có thể tạo implementation linh hoạt; đừng suy luận cấu trúc file dựa vào một cách triển khai. |
| “Bất kỳ method handle nào cũng có thể là bootstrap.” | Phải thỏa quy tắc về kiểu đối số/return và yêu cầu CallSite/type. |
| “Có `Dynamic` trong constant pool tức là có call site indy.” | `CONSTANT_Dynamic` là hằng động (condy); nó khác `CONSTANT_InvokeDynamic`. |
| “Tất cả tên synthetic lambda sẽ mãi theo một format.” | Tên/số lượng method sinh ra là chi tiết compiler. |
| “Bootstrap lỗi có thể retry tự do trong lần gọi tiếp theo.” | Resolution thất bại có quy tắc cache lỗi; không tự lặp lại bootstrap theo invocation. |

## 15. Khi nào lập trình viên cần biết cơ chế này?

Bạn sẽ thường **không cần tự tạo indy call site** trong code nghiệp vụ Java thường ngày. Kiến thức này hữu ích khi:

- **Đọc bytecode**: hiểu tại sao `javap -c` cho thấy `invokedynamic` dù trong Java source chỉ có lambda hoặc phép nối chuỗi.
- **Chẩn đoán lỗi linking**: khi thấy `BootstrapMethodError`/`LambdaConversionException` hoặc lỗi khi chạy class được sinh bằng bytecode tooling.
- **Phát triển compiler/ngôn ngữ JVM hoặc bytecode tooling**: tạo bootstrap, call site descriptors, dynamic constants, method handles và thông tin đúng trong `BootstrapMethods`.
- **Hiểu hiệu năng/JIT**: phân biệt chi phí liên kết một lần với invocation liên tục; đánh giá target ổn định so với target có thể đổi.
- **Thiết kế hệ thống thay đổi hành vi runtime**: dùng MethodHandle/CallSite API khi thực sự có nhu cầu dynamic linking, đồng thời kiểm soát thread safety, type safety và quan sát behavior.

Nếu chỉ muốn gọi method Java thông thường, không nên chọn invokedynamic như một “mẹo tối ưu” mặc định. Tập opcode `invoke*` mỗi loại có semantics riêng, và cách compiler sử dụng chúng phải dựa vào yêu cầu chương trình.

## 16. Tự kiểm tra

**Câu 1.** `invokedynamic` có bắt buộc chứa class owner của method target trong `CONSTANT_InvokeDynamic` không?

**Đáp án:** Không. Entry chứa **bootstrap_method_attr_index** và **name_and_type_index**. Bootstrap xác định target bằng cách trả CallSite, không phải chỉ theo owner/method reference như `Methodref`.

**Câu 2.** Chỉ số 0 ở `BootstrapMethods` có phải là `constant_pool[0]`?

**Đáp án:** Không. Danh sách `bootstrap_methods[]` bắt đầu tại 0, constant pool bắt đầu tại 1. Trong bootstrap entry còn có `bootstrap_method_ref` trỏ ngược đến constant pool.

**Câu 3.** Call site `(I)Ljava/lang/String;` nhận và trả những gì?

**Đáp án:** Mỗi invocation nhận một dynamic argument kiểu int từ operand stack và trả reference đến String trên stack. Nó không nói gì về số static bootstrap arguments.

**Câu 4.** Tại sao `LambdaMetafactory` cần implementation method handle?

**Đáp án:** Bootstrap cần biết logic phương thức nào sẽ được implementation của functional interface gọi. Method handle mô tả implementation, trong khi SAM method type và instantiated method type mô tả hợp đồng kiểu.

**Câu 5.** Gọi lambda `op.applyAsInt(5)` có phải luôn thực thi `invokedynamic` lần nữa không?

**Đáp án:** Không. `invokedynamic` thường được dùng để tạo factory/object lambda; gọi method trên object sau đó có thể dùng `invokeinterface` hoặc cơ chế gọi phù hợp khác.

**Câu 6.** Hai instruction indy ở vị trí bytecode khác nhau tham chiếu cùng entry constant pool. Có chắc chỉ một CallSite không?

**Đáp án:** Không. Linkage thành công được xác lập theo **instruction occurrence**, dù symbolic constant pool entry giống nhau.

**Câu 7.** Bootstrap trả `null` hoặc CallSite sai type thì sao?

**Đáp án:** Resolution call site thất bại, thường gây `BootstrapMethodError` theo quy tắc JVMS.

**Câu 8.** Tại sao bootstrap cần tránh side effect không idempotent?

**Đáp án:** Vì các thread có thể cạnh tranh resolve cùng site chưa liên kết, gây nhiều bootstrap execution, nhưng chỉ một kết quả được chọn.

**Câu 9.** `MutableCallSite` khác `ConstantCallSite` ở đâu?

**Đáp án:** Target của MutableCallSite có thể thay đổi bằng `setTarget` và cần chú ý visibility, còn ConstantCallSite giữ target không đổi sau khi khởi tạo thích hợp.

**Câu 10.** `CONSTANT_Dynamic` có mang method descriptor cho việc gọi method không?

**Đáp án:** Không. `CONSTANT_Dynamic` mô tả **hằng động** với **field descriptor**; method descriptor dành cho `CONSTANT_InvokeDynamic`.

**Câu 11.** Gặp `BootstrapMethodError` tại lần gọi đầu, có nên thử lặp lại cùng instruction để bootstrap tự chạy lại không?

**Đáp án:** Không. JVM duy trì trạng thái lỗi resolution theo đặc tả. Xác định và sửa vấn đề bootstrap, type, class loading hoặc dependency.

**Câu 12.** Nếu thay target của `MutableCallSite` trong một thread, mọi thread khác có lập tức thấy không?

**Đáp án:** Không tự động bảo đảm như volatile trong mọi trường hợp. Cần dùng `syncAll` hoặc thiết kế phù hợp như `VolatileCallSite` theo yêu cầu visibility.

## 17. Bản đồ ghi nhớ

~~~text
ClassFile
  |
  +-- constant_pool
  |     +-- CONSTANT_InvokeDynamic (tag 18)
  |             +-- NameAndType  -> name + METHOD descriptor
  |             +-- bootstrap_method_attr_index -> BootstrapMethods[k]
  |
  +-- BootstrapMethods attribute
        +-- bootstrap_method_ref  -> CONSTANT_MethodHandle
        +-- bootstrap_arguments[] -> CONSTANT_* hỗ trợ

Runtime:
  opcode invokedynamic #N, 0
             |
             v
      [site chưa liên kết?]
             |
             +-- yes -> resolve metadata -> gọi bootstrap
             |                            -> validate CallSite / type
             |                            -> gắn kết quả cho instruction
             |
             v
         target MethodHandle
             |
             +-- ConstantCallSite: target ổn định
             +-- MutableCallSite : target đổi được, chú ý sync
             +-- VolatileCallSite: target đổi được, volatile semantics
             |
             v
       dynamic args từ operand stack
             |
             v
       thực thi target -> push return nếu khác void
~~~

**Điểm quan trọng:** `invokedynamic` cung cấp **điểm gọi có liên kết tùy biến qua bootstrap**; `BootstrapMethods` mang phương thức và hằng phục vụ liên kết; `CallSite` là kết quả liên kết giữ target MethodHandle; còn các **dynamic arguments** chỉ được truyền khi thực sự gọi. Tách rõ bốn khái niệm này là chìa khóa để hiểu lambda, nối chuỗi và call-site linking ở JVM.

## Tài liệu tham khảo chính thức

- [JVMS Java SE 21 — `4.4.10` CONSTANT_InvokeDynamic và CONSTANT_Dynamic](https://docs.oracle.com/javase/specs/jvms/se21/html/jvms-4.html#jvms-4.4.10).
- [JVMS Java SE 21 — `4.7.23` BootstrapMethods attribute](https://docs.oracle.com/javase/specs/jvms/se21/html/jvms-4.html#jvms-4.7.23).
- [JVMS Java SE 21 — `5.4.3.6` Dynamic constant và call site resolution](https://docs.oracle.com/javase/specs/jvms/se21/html/jvms-5.html#jvms-5.4.3.6).
- [JVMS Java SE 21 — `6.5` invokedynamic](https://docs.oracle.com/javase/specs/jvms/se21/html/jvms-6.html#jvms-6.5.invokedynamic).
- [Java SE 17 API — CallSite](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/invoke/CallSite.html).
- [Java SE 17 API — LambdaMetafactory](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/invoke/LambdaMetafactory.html).
- [Java SE 17 API — StringConcatFactory](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/invoke/StringConcatFactory.html).
- [JDK 17 javap tool](https://docs.oracle.com/en/java/javase/17/docs/specs/man/javap.html).


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="stack-frames-and-calls-006">&lt;init&gt; và &lt;clinit&gt;: phương thức đặc biệt có vai trò khác nhau</a>

<details>
<summary>Xem chi tiết</summary>

# `<init>` và `<clinit>`: hai phương thức đặc biệt có vai trò khác nhau trong JVM

Trong ClassFile của Java, **`<init>`** và **`<clinit>`** là tên nội bộ của hai loại *method đặc biệt*, nhưng chúng phục vụ hai công việc khác nhau:

- **`<init>` — instance initialization method:** tương ứng với constructor của một class. Nó được gọi trong quy trình khởi tạo **mỗi đối tượng** bằng cơ chế gọi đặc biệt, điển hình là bytecode `invokespecial`. Một class có thể có nhiều `<init>` khác descriptor vì constructor có thể overload.
- **`<clinit>` — class/interface initialization method:** nếu có, chứa công việc khởi tạo tĩnh mà compiler tạo ra từ static field initializers và static initializer blocks. Nó được JVM **tự thực hiện khi class hoặc interface được khởi tạo theo quy tắc JVM**, thường chỉ một lần cho mỗi định nghĩa class/interface thành công, không phải một lần cho mỗi `new`.

Khi đọc kết quả `javap`, cần nhớ `<init>` không đồng nghĩa **toàn bộ khởi tạo object**, còn `<clinit>` không đồng nghĩa **nạp class file**. Class loading, linking, initialization và gọi constructor là những bước có liên quan nhưng khác nhau.

## 1. So sánh nhanh `<init>` và `<clinit>`

| Tiêu chí | `<init>` | `<clinit>` |
| --- | --- | --- |
| Mục đích | Khởi tạo một **instance** thông qua constructor | Khởi tạo trạng thái **static** của class/interface |
| Xuất hiện từ đâu? | Constructor viết trong source hoặc constructor mặc định do compiler tạo | Phần khởi tạo static cần thực thi do compiler tổng hợp |
| Bao nhiêu method? | Có thể nhiều, phân biệt bằng descriptor tham số | Tối đa một method mang tên `<clinit>` trong một ClassFile hợp lệ |
| Descriptor | Dạng `(tham_so)V`, trả `void` ở mức JVM | **`()V`**: không tham số, không trả giá trị |
| Cách khởi chạy | Thường dùng `invokespecial` với tham chiếu instance chưa được khởi tạo hoặc đang trong constructor chain | JVM kích hoạt theo quy trình class/interface initialization; không gọi trực tiếp như method Java |
| Liên quan tới `new` | Lời gọi constructor diễn ra khi tạo object qua biểu thức `new` trong chương trình Java thông thường | Nếu class chưa initialized, hoạt động `new` có thể làm class cần initialization trước khi tiếp tục |
| Tần suất | Mỗi lần có lời gọi constructor tương ứng trong quá trình tạo object | Một lần cho mỗi định nghĩa class/interface được initialize thành công, trừ trường hợp định nghĩa khác do class loader khác |
| Có kế thừa/override không? | Constructor không được kế thừa hay override; constructor chain có thể gọi `super(...)` hoặc `this(...)` | Static initialization của class cha xảy ra theo quy tắc trước class con; không có override `<clinit>` |
| Có thể gọi từ source Java theo tên `<init>`/`<clinit>`? | Không; dùng `new`, constructor chaining hoặc reflection API thích hợp | Không; chỉ viết static field initializer/static block hoặc để JVM kích hoạt |
| Có thể gây lỗi? | Exception từ constructor và quá trình khởi tạo instance | Exception/Error lúc khởi tạo class, có thể ảnh hưởng mọi lần sử dụng về sau |

**Cách nhớ:** `<init>` gắn với **một object** và constructor; `<clinit>` gắn với **trạng thái class**. Dù tên chứa cặp ngoặc nhọn góc, đây là những tên đặc biệt trong ClassFile/JVM, **không phải cú pháp khai báo method của Java source**.

## 2. Đặt đúng hai method trong vòng đời JVM

Một mô hình khái quát:

```text
      JVM cần sử dụng một class
                 │
                 ▼
       Loading: nạp định nghĩa class
                 │
                 ▼
       Linking: verify, prepare, resolve
                 │
                 ▼
     Initialization khi có yêu cầu
       ┌──────────────────────────┐
       │ Thực thi <clinit> nếu có │
       │ (khi đúng điều kiện)     │
       └──────────────────────────┘
                 │
                 ▼
          new một instance
                 │
                 ▼
     Constructor chain / <init>
                 │
                 ▼
        Dùng object đã tạo
```

Sơ đồ là luồng khái quát của việc **lần đầu tạo một object**; không phải mọi thao tác load class đều lập tức gây initialization, và trong quá trình thực thi có thể nạp thêm nhiều class. Khi class đã khởi tạo thành công, việc tạo object tiếp theo **không chạy `<clinit>` lần nữa**, nhưng vẫn chạy quá trình constructor thích hợp.

### Phân biệt ba ý niệm “khởi tạo”

1. **Preparation:** JVM chuẩn bị các static fields với giá trị mặc định hoặc giá trị `ConstantValue` theo quy tắc ClassFile, trước khi thực thi code static initializer.
2. **Class/interface initialization:** JVM thực hiện công việc khởi tạo class/interface, trong đó có `<clinit>` nếu compiler đã tạo.
3. **Instance initialization:** Sau khi đối tượng được cấp phát và các instance fields có giá trị mặc định, quy trình constructor chain và instance field initializers/instance initializer blocks tạo nên trạng thái ban đầu của từng object.

`static int total = compute();` thường cần code thực thi trong `<clinit>`; `int value = compute();` là instance field initializer và thường được compiler chèn vào constructor `<init>` ở vị trí phù hợp. Không có opcode chung tên “execute every initializer” trong bytecode mà mọi trường hợp đều đi qua.

## 3. `<init>`: instance initialization method

### 3.1. Constructor Java trở thành gì trong class file?

Cho source:

```java
class Point {
    int x;

    Point() {
        this(0);
    }

    Point(int value) {
        x = value;
    }
}
```

Đây là **class minh họa**; nó có thể biên dịch khi đặt trong `Point.java` mà không cần `public`. Trong ClassFile, hai constructor tương ứng hai `<init>` có descriptor khác nhau:

```text
Point.<init>:()V
Point.<init>:(I)V
```

- `()V` nghĩa là không nhận tham số và kết thúc bình thường không trả giá trị.
- `(I)V` nghĩa là nhận một `int` và trả `void` ở mức method descriptor.
- Cả hai được JVM nhìn dưới tên đặc biệt `<init>`, **không phải hai method Java thường tên `Point` để có thể override**.

Ở Java source, constructor **không khai báo return type**, kể cả `void`; ký hiệu `V` trong descriptor là quy ước JVM về cách method này kết thúc, không cho phép viết `void Point()` để khai báo constructor.

### 3.2. `new` không tự gọi tất cả constructor

Khi biên dịch biểu thức:

```java
Point point = new Point(5);
```

bytecode thường có mẫu:

```text
new           Point
dup
iconst_5
invokespecial Point."<init>":(I)V
astore_1
```

Đây là **đoạn bytecode minh họa**, trong đó offset, cách nạp số `5` và chỉ số local variable có thể khác tùy ngữ cảnh/compiler. Diễn giải:

1. `new` yêu cầu tạo instance; reference trên stack đang ở trạng thái **uninitialized** theo các quy tắc kiểm tra của JVM.
2. `dup` sao chép **giá trị reference**, không tạo object thứ hai.
3. `iconst_5` đưa tham số `5` lên operand stack.
4. `invokespecial` gọi đúng instance initialization method `Point.<init>(int)`.
5. Reference sau khi khởi tạo thành công có thể được lưu và sử dụng.

**Đừng sử dụng object trước khi hoàn thành khởi tạo một cách không hợp lệ.** Bytecode verifier theo dõi các dạng *uninitialized reference* và điều kiện gọi constructor. `<init>` là loại invocation đặc biệt, không phải method có thể gọi tùy ý bằng `invokevirtual`.

### 3.3. `this(...)`, `super(...)` và constructor chaining

Ở Java 17, constructor thông thường phải bắt đầu bằng constructor invocation phù hợp:

- `this(...)` chuyển sang một constructor **khác của cùng class**.
- `super(...)` chuyển sang constructor của **class cha**.
- Nếu không viết tường minh, `javac` thêm lời gọi `super()` khi hợp lệ.

Ví dụ `Point()` gọi `this(0)`, nên khi `new Point()`, constructor no-arg **không** tự chạy toàn bộ instance field initializers rồi mới chạy constructor `Point(int)` thêm lần nữa. Các initializer của object được bố trí vào luồng constructor sao cho chúng chạy đúng số lần và đúng thứ tự theo ngữ nghĩa Java.

Với object thuộc class con:

```text
new Child(...)
  → gọi constructor thích hợp của Child
    → constructor chain đến super(...)
      → constructor chain của các ancestor
      → phần instance initialization của superclass
    → phần instance initialization của Child
    → body của Child constructor
  → object sẵn sàng được trả về nếu không có exception
```

Đây là sơ đồ ngữ nghĩa; trong bytecode thực, **các lời gọi `<init>` được lồng nhau** và Java source có các quy tắc về đánh giá đối số, instance initializers và thứ tự constructor body. Hãy xem ví dụ chạy ở mục 6 để thấy output chính xác.

### 3.4. Default values, instance field initializer và constructor body

Khi object được cấp phát, các instance field bắt đầu với **default values** như `0`, `false`, `null`. Sau đó các constructor và instance initializers có thể ghi đè chúng. Với class con, constructor cha chạy trước phần khởi tạo instance của con theo quy tắc Java.

Ví dụ:

```java
class Example {
    int count = 3;

    {
        count += 2;
    }

    Example() {
        count += 4;
    }
}
```

Nếu tạo `new Example()`, field `count` cuối cùng là **9**: mặc định `0`, initializer thành `3`, instance initializer block cộng `2`, constructor body cộng `4`. Đây là class minh họa hợp lệ; không cần khai báo `main` nếu chỉ dùng để quan sát cấu trúc khi biên dịch.

### 3.5. `<init>` không được kế thừa hay gọi như phương thức thường

Class con không “thừa hưởng” các constructor của class cha. Lời gọi `super(...)` khởi tạo phần thuộc class cha thông qua một constructor được chọn, nhưng đó **không phải override hoặc dynamic dispatch của constructor**. Bạn không thể gọi constructor như `obj.Point()` sau khi object đã được tạo.

Reflection có API `Constructor.newInstance(...)` để tạo object theo constructor thích hợp; nó không biến `<init>` thành Java method công khai tên `<init>`. Việc dùng reflection vẫn phải tuân thủ quy tắc về access và khởi tạo class.

## 4. `<clinit>`: class/interface initialization method

### 4.1. Compiler tổng hợp static initializers

Ví dụ:

```java
class Settings {
    static int port = readDefaultPort();

    static {
        port += 10;
    }

    static int readDefaultPort() {
        return 8000;
    }
}
```

Trong ClassFile, compiler có thể tạo:

```text
Settings.<clinit>:()V
  // port = readDefaultPort();
  // port += 10;
  // return
```

Đây là **pseudocode** về các hoạt động do `<clinit>` thực hiện, không phải cú pháp để tạo một method trong Java source. Nếu class initialization thành công, `Settings.port` trở thành **8010**. Các thao tác static field initializer và static block thông thường được thực hiện **theo thứ tự xuất hiện trong source**, có các quy tắc đặc biệt cho constant variables và forward reference.

Nếu class **không có công việc static cần code khởi tạo**, compiler **không bắt buộc** tạo `<clinit>`. Chẳng hạn một class chỉ có các method thường hoặc `static final int MAX = 10;` có thể không cần body `<clinit>` riêng; hằng số loại này có thể được biểu diễn thông qua `ConstantValue`.

### 4.2. `<clinit>` có một descriptor cố định

Khác với overload của constructor:

```text
<init>:()V           // no-arg constructor
<init>:(I)V          // constructor nhận int
<init>:(Ljava/lang/String;)V
<clinit>:()V         // class/interface initialization, không tham số
```

Các method trong bảng dùng **tên đặc biệt và method descriptors**. `<clinit>` không có tham số và không được khai báo như một Java source method. Trong class file hiện đại, nó mang tính `static` theo quy tắc phiên bản ClassFile; bạn không thể chọn gọi nó bằng `someClass.<clinit>()` hay bằng cách đưa tên `<clinit>` vào một câu lệnh Java.

### 4.3. Khi nào JVM kích hoạt class initialization?

Theo mô hình JVM/JLS, class thường được khởi tạo ngay trước một số **active uses**, ví dụ:

- Tạo instance của class bằng `new` (nếu chưa initialized).
- Gọi static method được khai báo trong class, ví dụ `Service.run()`.
- Đọc static field không thuộc trường hợp constant variable được phép truy cập mà không initialization.
- Ghi static field theo quy tắc truy cập static.
- Một số thao tác reflection/MethodHandle hoặc yêu cầu cụ thể của runtime.

Ví dụ:

```java
class Counter {
    static int value = init();

    static int init() {
        System.out.println("Counter static init");
        return 7;
    }

    static void show() {
        System.out.println(value);
    }
}
```

Nếu một class khác gọi `Counter.show()` lần đầu, JVM phải bảo đảm `Counter` đã được initialized theo quy tắc trước khi phần code `show()` thực thi. Tuy vậy, chỉ **load** class hoặc lấy một class literal như `Counter.class` thường **chưa** tự kích hoạt initialization.

### 4.4. Static initializer không nhất thiết có mặt dù dùng class

`static final` **compile-time constant** là trường hợp cần phân biệt:

```java
class Constants {
    static final int LIMIT = 100;
    static int runtimeValue = compute();

    static int compute() {
        System.out.println("Constants initialized");
        return 2;
    }
}
```

Khi Java compiler có thể thay `Constants.LIMIT` bằng literal `100` theo quy tắc constant variables, lời đọc `Constants.LIMIT` **không tự yêu cầu khởi tạo `Constants`**. Nhưng truy cập `Constants.runtimeValue` thông thường kích hoạt initialization.

Không được khái quát thành “mọi `static final` đều không kích hoạt `<clinit>`”: `static final Object VALUE = new Object();` không phải compile-time constant variable, nên công việc khởi tạo object thuộc class initialization. Hãy phân biệt **hằng số lúc biên dịch** với **biến static final cần thực thi biểu thức lúc chạy**.

### 4.5. Interface và class có khác nhau không?

Interface cũng có thể cần initialization, đặc biệt khi có các static field với initializer tạo tác dụng phụ theo những ngữ nghĩa hợp lệ. Compiler có thể tạo `<clinit>` cho interface nếu cần.

Không suy luận “một class implement interface thì mọi interface cha đều được initialize như toàn bộ chuỗi superclass”. **Quy tắc khởi tạo superclass và superinterface có default method** khác với giả định khởi tạo toàn bộ đồ thị interface cùng lúc. Trong thực hành, đọc *JLS §12.4* và *JVMS §5.5* khi phải dự đoán thứ tự phức tạp.

## 5. Thứ tự và tính duy nhất của class initialization

### 5.1. Một lần trên mỗi định nghĩa class, không phải một lần trên toàn ứng dụng

Trong một JVM, danh tính lớp phụ thuộc vào **binary name** và **defining class loader**. Hai class loaders khác nhau có thể định nghĩa hai class cùng binary name nhưng là **hai runtime classes khác nhau**, với static state riêng và quy trình initialization riêng.

Vì vậy câu “`<clinit>` chỉ chạy một lần” cần nói chính xác: **mỗi runtime class/interface definition được initialization thành công chỉ thực hiện quy trình khởi tạo của nó một lần**. Nếu tải định nghĩa độc lập qua loader khác, đó là trường hợp khác. Không có cam kết chỉ một lần trên mọi tiến trình Java trong mọi hệ thống.

### 5.2. Khởi tạo superclass trước subclass

Khi initialization của subclass được yêu cầu, JVM bảo đảm superclass liên quan được initialized trước theo các quy tắc áp dụng. Vì vậy:

```text
First active use of Child
  → Parent class initialization (<clinit> nếu có)
  → Child class initialization (<clinit> nếu có)
  → constructor chain của object:
      Parent.<init> ...
      Child.<init> ...
```

**Class initialization** là công việc ở mức static, còn **constructor chain** là công việc trên từng instance. Hai chuỗi này liên quan nhau vì `new Child()` cần class initialized trước khi object được tạo/khởi tạo đầy đủ, nhưng không nên gộp chúng thành một `<init>`.

### 5.3. Thread safety và trạng thái lỗi

JVM có cơ chế phối hợp initialization của một class để những luồng đồng thời không cùng chạy nội dung `<clinit>` như hai tác vụ độc lập trên cùng một runtime class. Các thread khác có thể cần chờ kết quả initialization; JVM có các quy tắc về trạng thái đang khởi tạo, đã khởi tạo và **erroneous**.

Nếu `<clinit>` kết thúc bất thường, việc dùng class có thể thất bại với `ExceptionInInitializerError` (khi một exception phù hợp được bao bọc) hoặc lỗi tương ứng; những lần active use về sau thường nhận `NoClassDefFoundError` cho class ở trạng thái initialization thất bại. Trường hợp lỗi `Error` có các quy tắc riêng; không phải mọi lỗi luôn được bọc cùng một cách.

Việc bắt lỗi rồi “gọi lại `<clinit>`” theo tên là **không có** trong ngữ nghĩa Java. Cách khắc phục thường là sửa nguyên nhân initialization, thay đổi cấu trúc thiết kế hoặc trong môi trường ứng dụng phù hợp nạp định nghĩa mới qua loader/tiến trình mới; không tìm cách gọi trực tiếp `<clinit>`.

## 6. Ví dụ chạy Java 17+: thứ tự `<clinit>`, `<init>` và constructor chaining

Tạo file **`InitLifecycleDemo.java`**:

```java
public class InitLifecycleDemo {
    static int log(String text) {
        System.out.println(text);
        return 1;
    }

    static class Parent {
        static int staticValue = log("Parent static initializer");

        int parentValue = log("Parent instance initializer");

        Parent() {
            System.out.println("Parent constructor");
        }
    }

    static class Child extends Parent {
        static int staticValue = log("Child static field initializer");

        static {
            System.out.println("Child static block");
        }

        int childValue = log("Child instance initializer");

        Child() {
            System.out.println("Child constructor");
        }

        Child(int number) {
            this();
            System.out.println("Child constructor(int): " + number);
        }
    }

    public static void main(String[] args) {
        System.out.println("start");

        System.out.println("first new");
        Child first = new Child(7);

        System.out.println("second new");
        Child second = new Child();

        System.out.println("different objects: " + (first != second));
    }
}
```

Từ thư mục chứa source, sử dụng JDK 17 trở lên:

```bash
javac -encoding UTF-8 InitLifecycleDemo.java
java InitLifecycleDemo
```

**Standard output chính xác:**

```text
start
first new
Parent static initializer
Child static field initializer
Child static block
Parent instance initializer
Parent constructor
Child instance initializer
Child constructor
Child constructor(int): 7
second new
Parent instance initializer
Parent constructor
Child instance initializer
Child constructor
different objects: true
```

### Giải thích theo từng giai đoạn

**Lần tạo object đầu tiên:**

1. `main` in `start`, rồi `first new`.
2. Active use `new Child(7)` cần initialization của `Child`; `Parent` được initialized trước → `Parent static initializer`.
3. `Child` được initialized → field static initializer in `Child static field initializer`, static block in `Child static block`. Hai thành phần tĩnh của **Child** thực hiện theo thứ tự source.
4. `Child(int)` gọi `this()`, và `Child()` có lời gọi `super()` ngầm định. Luồng constructor đến **Parent** trước.
5. Instance field initializer của `Parent` in `Parent instance initializer`, rồi body của `Parent()` in `Parent constructor`.
6. Instance field initializer của `Child` in `Child instance initializer`, rồi body `Child()` in `Child constructor`.
7. Trở về body của `Child(int)` → in `Child constructor(int): 7`.

**Lần tạo object thứ hai:**

- `Parent` và `Child` đã initialized thành công, nên **không in** ba dòng static initializer nữa.
- Object mới vẫn thực hiện instance initializers của cha/con và constructor bodies như bình thường.
- `first != second` là `true` vì `new` tạo các object riêng; việc `<clinit>` chạy một lần không có nghĩa chương trình chỉ tạo được một instance.

**Lưu ý chính xác:** `Parent static initializer` và `Child static field initializer` trong ví dụ là *chuỗi log*, không phải tên bytecode instruction. Lệnh thực hiện việc in được compiler đưa vào `<clinit>` theo cấu trúc source, còn constructor/instance initializers được chuyển thành bytecode trong các `<init>` tương ứng.

## 7. Thử nghiệm riêng: `static final`, `T.class` và active use

Để phân biệt load/reference class với initialization, tạo file **`ActiveUseDemo.java`**:

```java
public class ActiveUseDemo {
    static class Lazy {
        static final int LIMIT = 100;

        static int result = init();

        static int init() {
            System.out.println("Lazy initialized");
            return 25;
        }

        static void work() {
            System.out.println("Lazy.work()");
        }

        Lazy() {
            System.out.println("Lazy constructor");
        }
    }

    public static void main(String[] args) {
        System.out.println("begin");
        System.out.println("constant: " + Lazy.LIMIT);
        System.out.println("class literal: " + (Lazy.class != null));
        System.out.println("before active use");
        System.out.println("runtime field: " + Lazy.result);
        Lazy.work();
        new Lazy();
        System.out.println("end");
    }
}
```

Biên dịch/chạy:

```bash
javac -encoding UTF-8 ActiveUseDemo.java
java ActiveUseDemo
```

**Standard output:**

```text
begin
constant: 100
class literal: true
before active use
Lazy initialized
runtime field: 25
Lazy.work()
Lazy constructor
end
```

Ý nghĩa:

- `Lazy.LIMIT` là **compile-time constant variable**; compiler có thể inline `100`, nên thao tác này không gây initialization của `Lazy`.
- `Lazy.class != null` dùng class literal; hoạt động này không phải active use đòi `<clinit>`. Nó có thể khiến hệ thống lấy đối tượng `Class` của type nhưng **không** đồng nghĩa khởi tạo static state.
- Lần đọc **`Lazy.result`** kích hoạt `Lazy` initialization, in một lần `Lazy initialized`.
- Gọi `Lazy.work()` và `new Lazy()` sau đó không khởi tạo lại static fields. Tuy nhiên **`new Lazy()` vẫn gọi constructor** nên có `Lazy constructor`.
- Nếu đảo thứ tự để `new Lazy()` diễn ra đầu tiên, class initialization sẽ diễn ra trước khi constructor body chạy.

Chương trình không dùng reflection hay class loader tùy biến nên thứ tự stdout trên có thể kiểm tra nhất quán.

## 8. Đọc bytecode bằng `javap`

Các lệnh sau dùng output `.class` của ví dụ mục 6:

```bash
javap -c -p InitLifecycleDemo
javap -c -p 'InitLifecycleDemo$Parent'
javap -c -p 'InitLifecycleDemo$Child'
javap -v -p 'InitLifecycleDemo$Child'
```

Trong Bash/PowerShell, nháy đơn bảo vệ ký tự `$` trong tên binary của nested class. Với Windows `cmd.exe`, dùng dấu nháy kép xung quanh `InitLifecycleDemo$Child`.

### 8.1. Tìm constructor `<init>`

Ở output `javap -c -p 'InitLifecycleDemo$Child'`, các constructor có thể được hiển thị bằng **tên class**:

```text
InitLifecycleDemo$Child();
InitLifecycleDemo$Child(int);
```

Đừng suy ra trong class file constructor thật mang tên `Child`; **constant pool và các method references** sẽ thể hiện special name `<init>`. Một đoạn điển hình trong constructor no-arg:

```text
aload_0
invokespecial InitLifecycleDemo$Parent."<init>":()V
aload_0
invokestatic InitLifecycleDemo.log:(Ljava/lang/String;)I
putfield ... childValue:I
...
return
```

Đây là mẫu được rút gọn để giải thích, **không phải listing `javap` cần khớp từng offset**. Constructor `Child(int)` sẽ gọi `Child.<init>:()V` bằng `invokespecial` trước khi in dòng nhận tham số.

### 8.2. Tìm `<clinit>`

Ở output của class **Child**, `javap` thường trình bày class initialization method bằng nhãn:

```text
static {};
```

Phần body chứa bytecode cho:

```java
static int staticValue = log("Child static field initializer");
static {
    System.out.println("Child static block");
}
```

Đó là body `<clinit>:()V` do compiler tạo. Các lệnh thường liên quan tới gọi `log`, `putstatic`, in ra console rồi `return`.

Tại `javap -v -p`, quan sát **method descriptor `()V`** và những mục constant pool hoặc tên `<clinit>`, `<init>` khi chúng xuất hiện. `javap` có thể hiển thị constructor bằng tên class và static initializer là `static {}`, vì vậy đừng tìm một dòng hiển thị giống hệt `void <clinit>()` trong phần Java-like signatures.

### 8.3. So sánh code khởi tạo instance với code static

| Java source | Thông tin bytecode thường thấy |
| --- | --- |
| `static int staticValue = log(...);` | Trong `<clinit>`: gọi `log`, rồi `putstatic` |
| `static { System.out.println(...); }` | Trong `<clinit>`: các instruction từ static block |
| `int childValue = log(...);` | Trong constructor `<init>`, sau lời gọi constructor cha thích hợp |
| `Child()` | Một `<init>:()V` |
| `Child(int)` | Một `<init>:(I)V` gọi `this()` |
| `new Child(7)` | `new`, nạp tham số, `invokespecial <init>` tại code gọi |
| `Child.staticValue` | `getstatic`, có thể kích hoạt class initialization khi class chưa initialized |

Chi tiết instruction có thể thay đổi khi đổi compiler, nhưng ranh giới **static state khởi tạo một lần** và **instance state khởi tạo cho từng object** vẫn tuân theo ngữ nghĩa Java.

## 9. Các câu hỏi dễ nhầm khi debug chương trình

### 9.1. “Có constructor nhưng tại sao chưa thấy dòng static log?”

Nếu chỉ viết class mà chưa active-use class, **class initialization có thể chưa xảy ra**. Những thao tác như biên dịch, kiểm tra class file bằng `javap`, lấy tên lớp từ metadata hoặc dùng class literal không đồng nghĩa đã chạy static initializer. Ngược lại, lần đầu thực sự gọi static method hoặc tạo instance có thể kích hoạt initialization.

### 9.2. “Constructor nào chạy khi `this(...)` xuất hiện?”

Constructor được gọi qua `this(...)` chạy trên **cùng object**. Đây không phải tạo object thứ hai. Quá trình đi tới một constructor có lời gọi `super(...)`, rồi quay lại lần lượt các constructor trong chain. Instance initialization blocks của class không chạy lặp một lần cho mỗi constructor cùng class nằm trong chain.

### 9.3. “Static field có giá trị mặc định trước `<clinit>` không?”

Có. JVM thực hiện **preparation** và áp dụng các quy tắc cho static fields/constant values. Khi `<clinit>` chạy, code có thể đọc các giá trị mặc định hoặc giá trị đã được khởi tạo trước theo quy tắc, rồi ghi tiếp. Đừng đồng nhất việc field ban đầu có `0` với chuyện `<clinit>` đã chạy.

### 9.4. “Gọi `super()` có khởi tạo static field của class cha không?”

Class initialization của superclass được JVM bảo đảm theo quy trình class initialization phù hợp khi cần khởi tạo subclass. Nhưng **`super()` là constructor invocation**, dùng để khởi tạo phần instance của cha. Đây là hai khái niệm khác; không dùng tên `super()` thay cho `<clinit>`.

### 9.5. “`<clinit>` có được JVM thực thi lại khi tạo object thứ hai không?”

Không, nếu runtime class đã initialized thành công. Mỗi object mới vẫn có quá trình constructor riêng. Nếu định nghĩa class khác được nạp qua loader khác hoặc JVM mới, đó lại là runtime class khác.

### 9.6. “Tại sao một static initializer lỗi làm cả class khó dùng?”

Lỗi phát sinh trong quá trình class initialization có thể đưa runtime class vào trạng thái **erroneous**. Những lần active-use về sau không đơn giản thử lại initializer như một function gọi thông thường. Thiết kế static initializer nên tránh công việc bất ổn khó phục hồi như truy cập mạng, đọc cấu hình không kiểm soát và thao tác phụ thuộc tài nguyên chưa sẵn sàng.

### 9.7. “Có `<clinit>` trong mọi class chứa `static` không?”

Không. Class có `static` methods hoặc compile-time constants chưa chắc cần `<clinit>`; compiler chỉ tạo khi cần các công việc khởi tạo class/interface phải thực thi thành bytecode. Ngược lại, class không có static block tường minh **vẫn** có thể có `<clinit>` vì static field initializer có biểu thức cần chạy.

### 9.8. “`<init>` có thể trả một object khác thay thế `this` không?”

Constructor Java không có return value và `<init>` có descriptor trả `V`. Factory method có thể trả object do nó chọn, nhưng đó là **method bình thường** chứ không phải constructor `<init>`. Kết quả của biểu thức `new` là tham chiếu tới object vừa được tạo/khởi tạo thành công.

## 10. Bài tập vận dụng

### Bài tập 1 — Đếm số lần

Xét:

```java
class Service {
    static {
        System.out.println("S");
    }

    {
        System.out.println("I");
    }

    Service() {
        System.out.println("C");
    }
}
```

Nếu một `main` khác lần lượt gọi `new Service(); new Service();` và không có active use trước đó, output gồm:

```text
S
I
C
I
C
```

Giải thích: static initialization chỉ diễn ra lần đầu; instance initializer và constructor body chạy cho **mỗi object**.

### Bài tập 2 — Constructor chaining

Xét:

```java
class Sample {
    {
        System.out.println("I");
    }

    Sample() {
        this(1);
        System.out.println("A");
    }

    Sample(int n) {
        System.out.println("B");
    }
}
```

Nếu `new Sample()`, output là:

```text
I
B
A
```

Instance initializer **chỉ chạy một lần** trên object. `Sample()` chuyển sang `Sample(int)`, khởi tạo instance và chạy body constructor nhận `int`, rồi mới trở lại in `A`.

### Bài tập 3 — Hằng số compile-time

Xét:

```java
class Marks {
    static final int LIMIT = 50;
    static {
        System.out.println("initialized");
    }
}
```

Nếu `main` chỉ in `Marks.LIMIT` từ một class được biên dịch cùng code, JVM có thể chỉ thực thi code đã inline `50`; không bắt buộc in `initialized`. Nếu thêm một active use chắc chắn như gọi một static method thực thi của `Marks` hoặc tạo `new Marks()`, initialization sẽ xảy ra trước thao tác tương ứng.

### Bài tập 4 — Dự đoán `javap`

Từ `InitLifecycleDemo.java`:

1. `Child()` có descriptor nào? **`()V`**.
2. `Child(int)` có descriptor nào? **`(I)V`**.
3. `Child static block` nằm trong method nào? **`<clinit>` của Child**.
4. `Child instance initializer` thuộc quá trình nào? **Constructor `<init>`**, không phải `<clinit>`.
5. Dòng `Child constructor(int): 7` in trước hay sau `Child constructor`? **Sau**, bởi `Child(int)` gọi `this()`.
6. Có bao nhiêu object `Child` được tạo trong `main`? **Hai**, mặc dù static initializer chỉ chạy một lần.

### Câu hỏi tự kiểm tra

**1.** `<init>` và `<clinit>` có chung mục đích “đặt giá trị mặc định cho mọi field” không?  
**Không.** Default values có quy trình riêng tại preparation/object allocation; `<init>` thực hiện constructor logic trên instance, `<clinit>` thực hiện static initialization logic nếu có.

**2.** Có thể overload `<clinit>` thành `<clinit>(int)` trong class file không?  
**Không.** Class/interface initialization method có descriptor quy định `()V`; không phải API do người dùng overload.

**3.** Tạo 100 instance cùng một class đã initialized thì `<init>` và `<clinit>` chạy bao nhiêu lần?  
Mỗi instance được khởi tạo qua constructor chain thích hợp; **`<clinit>` không chạy lại** vì class đã initialized thành công. Số lời gọi `<init>` thực tế có thể lớn hơn 100 do mỗi object có chuỗi constructor của cha/con.

**4.** Vì sao `javap` đôi khi chỉ in `static {}` mà không hiện đúng chuỗi `<clinit>` ở dòng method header?  
`javap` hiển thị biểu diễn dễ đọc gần cú pháp Java; tên method đặc biệt `<clinit>` được biểu diễn thành static initializer.

**5.** `<init>` được gọi bằng `invokevirtual` như override method không?  
**Không.** Constructor được gọi theo cơ chế `invokespecial`; nó không là method thường để override.

**6.** Khi class có `static final String CODE = "OK";`, việc đọc CODE chắc chắn kích hoạt static block?  
**Không.** Nếu `CODE` là compile-time constant variable và được inline theo quy tắc Java, chỉ đọc nó chưa phải active use khởi tạo class.

**7.** Class loading có đồng nghĩa với class initialization không?  
**Không.** Có thể load/link class mà chưa chạy `<clinit>`; initialization được kích hoạt tại thời điểm thích hợp theo các quy tắc JVM.

**8.** Nếu một static initializer thất bại, gọi `new` lần nữa có chắc initializer được chạy lại không?  
**Không.** Class có thể ở trạng thái erroneous trong class loader hiện tại, nên việc active-use tiếp tục thất bại.

## 11. Ghi nhớ và tài liệu chuẩn

- **`<init>`**: tên JVM của constructor; có thể overload theo descriptor; tham gia instance initialization, thường thấy qua `invokespecial`.
- **`<clinit>`**: method do compiler tổng hợp cho công việc class/interface initialization; descriptor `()V`; JVM quản lý khi nào thực thi.
- **Static initialization không phải instance initialization**: `<clinit>` xử lý trạng thái static, constructor chain/`<init>` xử lý state mỗi object.
- **Loading không phải initialization**: xem trình tự loading, linking, preparation, initialization thay vì mặc định tất cả xảy ra ngay sau khi đọc tệp class.
- **Kiểm chứng bằng `javap -c -p` và `javap -v`**: dự đoán output chương trình, sau đó đọc các method, descriptors và instruction liên quan.

Tài liệu chuẩn: *The Java Virtual Machine Specification*, chương 2 (*instance initialization methods* và *class/interface initialization methods*), chương 4 (*ClassFile*), chương 5.5 (*Initialization*) và chương 6.5 (`new`, `invokespecial`), bản Java 17: https://docs.oracle.com/javase/specs/jvms/se17/html/ . Quy tắc thứ tự initializer và constructor ở cấp ngôn ngữ được giải thích trong *Java Language Specification*, các mục **§8.8** và **§12.4**.

**Kết luận:** Hãy đặt câu hỏi đúng cấp độ: “JVM đã **initialize class** chưa?” để hiểu `<clinit>`; và “JVM đang **khởi tạo object nào, qua constructor chain nào**?” để hiểu `<init>`. Khi hai câu hỏi được tách rõ, thứ tự log static/instance, lỗi initialization và output của `javap` trở nên nhất quán, dễ dự đoán hơn.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="stack-frames-and-calls-007">return và các biến thể khi lời gọi hoàn thành bình thường</a>

<details>
<summary>Xem chi tiết</summary>

# `return` và các biến thể khi lời gọi hoàn thành bình thường

## 1. Mục tiêu: “trở về” ở cấp Java và ở cấp JVM là gì?

Trong Java source, từ khóa `return` làm kết thúc việc thực thi phương thức hiện tại theo nhánh bình thường, với một giá trị hoặc không có giá trị. Ở cấp JVM, **một lời gọi phương thức hoàn tất bình thường (normal method invocation completion)** khi phương thức đã được gọi đi đến một trong **sáu bytecode return phù hợp với kiểu trả về** của nó và không có exception làm thất bại quá trình hoàn tất.

Theo **JVMS `2.6.4`**, khi callee hoàn tất bình thường, JVM:

1. Hoàn tất các công việc bắt buộc trước khi thực sự kết thúc lời gọi, chẳng hạn chạy `finally` tương ứng và xử lý monitor của `synchronized method` theo ngữ nghĩa liên quan.
2. Nếu có giá trị trả về, chuyển đúng giá trị đó từ **operand stack của frame callee** sang **operand stack của frame caller**.
3. Kết thúc frame callee, khôi phục frame caller thành **current frame**.
4. Tiếp tục thực thi caller tại vị trí logic **ngay sau instruction gọi phương thức**.

Với phương thức trả `void`, không có giá trị được đẩy lên operand stack của caller.

~~~text
frame(caller)                          frame(callee)
  locals: ...                            locals: parameters ...
  operand stack trước invoke: [args]      operand stack khởi đầu: []
          |                                     |
          +------- invoke method ------------> |
                                                | thực thi code
                                                | ... -> [result]
          <----------- ireturn <----------------+
  operand stack sau invoke: [result]      frame callee kết thúc
  caller tiếp tục sau invoke
~~~

Sơ đồ biểu diễn **ngữ nghĩa frame theo đặc tả JVM**. JIT compiler có thể tối ưu hoặc inline method; không bắt buộc mỗi lời gọi phải có một native memory block tách rời nếu hành vi Java/JVMS được bảo toàn.

Phân biệt ngay ba khái niệm:

- **`return` ở source Java:** phát biểu điều khiển luồng, có thể trả biểu thức hoặc kết thúc `void` method.
- **Các opcode `return` trong bytecode:** những lệnh máy ảo thực sự kết thúc method theo đường bình thường, chọn theo **return descriptor**.
- **`ret` trong bytecode lịch sử:** nhảy về điểm sau `jsr` bên **trong cùng method**; **không phải** opcode trả giá trị cho method caller, không nên gộp vào sáu opcode của bài này.

## 2. Bảng sáu opcode: nhìn tên để biết giá trị nào trở về

JVMS `2.11.8` và `6.5` định nghĩa các lệnh return:

| Opcode JVM | Byte opcode (hex) | Kiểu trả về method hợp lệ | Giá trị phải có ở đỉnh operand stack |
| --- | --- | --- | --- |
| **`return`** | `0xb1` | `void`; constructor `<init>`, class initializer `<clinit>` | Không có giá trị cần trả |
| **`ireturn`** | `0xac` | `int`, **`boolean`, `byte`, `short`, `char`** | Một giá trị **kiểu `int`** |
| **`lreturn`** | `0xad` | `long` | Một giá trị `long` |
| **`freturn`** | `0xae` | `float` | Một giá trị `float` |
| **`dreturn`** | `0xaf` | `double` | Một giá trị `double` |
| **`areturn`** | `0xb0` | Các **reference types**: class, interface, array | Một giá trị `reference` tương thích descriptor |

Tại sao có **sáu** chứ không phải một opcode riêng cho từng kiểu Java?

- JVM dùng `int` làm kiểu tính toán chung cho `boolean`, `byte`, `char`, `short` trong nhiều ngữ cảnh bytecode. Vì vậy cả năm loại đó cùng dùng **`ireturn`**.
- `long`, `float`, `double` cần các opcode riêng phù hợp kiểu operand và cấu trúc thực thi.
- Object, array và interface values là **reference**; chúng cùng dùng **`areturn`**.
- `void` không có giá trị, nên dùng **`return`**, không dùng một giá trị giả như `null` hay `0`.

**Quy tắc verifier:** `ireturn` không hợp lệ nếu method descriptor yêu cầu `long`; `areturn` không hợp lệ nếu descriptor yêu cầu `int`; `return` không được dùng để kết thúc bình thường method khai báo trả về `String`. Các quy tắc ràng buộc này thuộc **JVMS `4.9.2`**.

### Kiểu `boolean`, `byte`, `short`, `char` đi qua `ireturn` như thế nào?

Trong frame callee, giá trị dùng bởi `ireturn` được biểu diễn là `int`. Trước khi chuyển kết quả sang caller, JVM có các chuyển đổi cần thiết theo return type đã khai báo:

- `byte`: chuyển tương đương `i2b` (thu hẹp có dấu).
- `short`: tương đương `i2s`.
- `char`: tương đương `i2c` (thu hẹp không dấu).
- `boolean`: chỉ lấy bit thấp, tức phép `AND 1` đối với giá trị `int` trong ngữ nghĩa `ireturn`.
- `int`: giữ kiểu `int`.

Compiler Java bình thường đã thực hiện các chuyển đổi cần thiết theo quy tắc ngôn ngữ, nhưng bộ kiểm tra bytecode và đường return của JVM vẫn phải tuân thủ kiểu khai báo. **Không có `breturn`, `creturn`, `sreturn`, `zreturn`** trong tập opcode JVM.

Các giá trị `long`/`double` chiếm **hai slot** trong mô hình operand stack; chúng vẫn là **một giá trị logic** được chuyển qua `lreturn`/`dreturn`. `float` và `reference` thuộc category 1.

## 3. Cấu trúc frame trước và sau return

Một lần gọi Java method có frame với **local variables array**, **operand stack** và thông tin liên kết động qua run-time constant pool. Trước khi callee thực thi, arguments được đưa vào locals của callee; operand stack của frame mới bắt đầu rỗng.

Ví dụ:

~~~java
static int add(int x, int y) {
    int result = x + y;
    return result;
}
~~~

Đây là method minh họa, cần đặt **bên trong một class** để biên dịch. Một dạng bytecode phù hợp do `javac` sinh:

~~~text
static int add(int, int);
  descriptor: (II)I
  Code:
     0: iload_0
     1: iload_1
     2: iadd
     3: istore_2
     4: iload_2
     5: ireturn
~~~

Nếu caller gọi `add(4, 5)`:

~~~text
Caller frame trước invoke:
  operand stack: [4, 5]
  instruction: invokestatic add:(II)I

Callee frame lúc vào add:
  locals: slot0=x=4; slot1=y=5; slot2=result(chưa gán)
  operand stack: []

Sau iload_0:  [4]
Sau iload_1:  [4, 5]
Sau iadd:     [9]
Sau istore_2: []; local result=9
Sau iload_2:  [9]
Sau ireturn:  callee hoàn tất, result=9 về caller

Caller frame sau invoke:
  operand stack: [9]
  instruction tiếp: có thể là istore, iadd, ireturn,...
~~~

**Giá trị trả về được chuyển về operand stack của caller, không tự động gán vào một biến source.** Nếu caller viết `int answer = add(4,5);`, bytecode caller thường dùng thêm `istore` để ghi giá trị đó vào local slot. Nếu caller chỉ viết `add(4,5);` và bỏ kết quả, compiler vẫn thực hiện lời gọi rồi có thể dùng `pop` để bỏ giá trị không dùng.

Đối với `void` method, operand stack caller sau invoke **không có thêm result**, vì `return` không đưa một giá trị nào trở về. Caller cũng không thể dùng kết quả `void` trong phép cộng như một `int`.

### Quy tắc “đường sau invoke”

Khi method trả về bình thường, **PC của caller** tiếp tục sau bytecode instruction dùng để gọi method: `invokestatic`, `invokevirtual`, `invokeinterface`, `invokespecial` hoặc `invokedynamic`. Địa chỉ trong mô tả này là vị trí instruction của **caller**; không nên hiểu JVM nhảy theo số dòng Java hay theo constant-pool index.

Ví dụ:

~~~text
Caller:
  0: iconst_4
  1: iconst_5
  2: invokestatic #N   // add:(II)I
  5: istore_1
  6: ...
~~~

`#N` là constant-pool index để xác định lời gọi; `5` là **BCI** ngay sau instruction invoke trong đoạn mã minh họa; `1` trong `istore_1` là **local slot index**. Chúng là ba không gian chỉ số khác nhau.

## 4. Mã Java 17+ chạy được: sáu nhóm kết quả

Tạo file **`ReturnOpcodes.java`** (một chương trình Java 17+ độc lập):

~~~java
public class ReturnOpcodes {
    static void noValue() {
        System.out.println("inside void");
    }

    static int anInt() {
        return 7;
    }

    static boolean aBoolean() {
        return true;
    }

    static byte aByte() {
        return 12;
    }

    static short aShort() {
        return 300;
    }

    static char aChar() {
        return 'A';
    }

    static long aLong() {
        return 4_000_000_000L;
    }

    static float aFloat() {
        return 1.5f;
    }

    static double aDouble() {
        return 2.25;
    }

    static String aReference() {
        return "JVM";
    }

    static int[] anArray() {
        return new int[] {2, 3};
    }

    static String aNullReference() {
        return null;
    }

    public static void main(String[] args) {
        noValue();
        System.out.println("int=" + anInt());
        System.out.println("boolean=" + aBoolean());
        System.out.println("byte=" + aByte());
        System.out.println("short=" + aShort());
        System.out.println("char=" + aChar());
        System.out.println("long=" + aLong());
        System.out.println("float=" + aFloat());
        System.out.println("double=" + aDouble());
        System.out.println("reference=" + aReference());
        System.out.println("array=" + java.util.Arrays.toString(anArray()));
        System.out.println("null reference=" + aNullReference());
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac -g ReturnOpcodes.java
java ReturnOpcodes
~~~

**Stdout chính xác:**

~~~text
inside void
int=7
boolean=true
byte=12
short=300
char=A
long=4000000000
float=1.5
double=2.25
reference=JVM
array=[2, 3]
null reference=null
~~~

Tại sao output giúp phân biệt bytecode?

- `noValue()`: in thông điệp, sau đó dùng opcode `return` cuối method (dù trong source không viết `return;` tường minh).
- `anInt()`, `aBoolean()`, `aByte()`, `aShort()`, `aChar()`: cả năm đều kết thúc bằng **`ireturn`** trên class file hợp lệ.
- `aLong()`, `aFloat()`, `aDouble()`: lần lượt **`lreturn`**, **`freturn`**, **`dreturn`**.
- `aReference()`, `anArray()` và `aNullReference()`: đều là **`areturn`** dù kết quả có thể là String reference, array reference hoặc `null`. `null` là một giá trị reference hợp lệ, **không có opcode `nullreturn`**.

Trong Java source, `boolean` là kiểu riêng; ở JVM, `ireturn` sử dụng `int` representation theo rules. Chương trình trên **không** kiểm tra hành vi abnormal bytecode; nó là bằng chứng phổ thông cho cách compiler hạ type return trong class file.

### Dùng `javap` để kiểm chứng opcode thực tế

~~~bash
javap -c -p ReturnOpcodes
javap -s -p ReturnOpcodes
javap -v -p ReturnOpcodes
~~~

Cách đọc:

1. Với `-s`, nhìn descriptor `()V`, `()I`, `()Z`, `()B`, `()S`, `()C`, `()J`, `()F`, `()D`, `()Ljava/lang/String;`, `()[I`.
2. Với `-c`, tìm opcode cuối các method tương ứng: `return`, `ireturn`, `lreturn`, `freturn`, `dreturn`, `areturn`.
3. Với `-v`, đọc `Code: stack=..., locals=...` cùng line numbers/constant pool. `javac -g` giúp thấy debug information để liên hệ source.
4. Tránh dùng duy nhất số bytecode offset và constant-pool index được mô tả trong bài làm “giá trị chuẩn”: chúng có thể khác theo JDK/compiler hoặc target release.

Ví dụ phần disassembly **minh họa cấu trúc, không cam kết BCI/constant-pool index cố định**:

~~~text
static int anInt();
  descriptor: ()I
  Code:
     0: bipush        7
     2: ireturn

static long aLong();
  descriptor: ()J
  Code:
     0: ldc2_w        #...   // long 4000000000l
     3: lreturn

static java.lang.String aReference();
  descriptor: ()Ljava/lang/String;
  Code:
     0: ldc           #...   // String JVM
     2: areturn

static void noValue();
  descriptor: ()V
  Code:
     ... invokevirtual #... // PrintStream.println
     ... return
~~~

Ở ví dụ source trên, một compiler có thể chọn instruction khác để nạp hằng, nhưng lệnh **return phù hợp descriptor** phải giữ quy tắc của JVM.

## 5. Descriptor quyết định opcode return hợp lệ

**Method descriptor** trong class file cho biết dạng `(parameter types)return type`, ví dụ:

| Method Java | JVM descriptor | Opcode return |
| --- | --- | --- |
| `void write()` | `()V` | `return` |
| `int sum(int,int)` | `(II)I` | `ireturn` |
| `boolean valid()` | `()Z` | `ireturn` |
| `byte code()` | `()B` | `ireturn` |
| `short id()` | `()S` | `ireturn` |
| `char initial()` | `()C` | `ireturn` |
| `long count()` | `()J` | `lreturn` |
| `float ratio()` | `()F` | `freturn` |
| `double average()` | `()D` | `dreturn` |
| `String label()` | `()Ljava/lang/String;` | `areturn` |
| `int[] numbers()` | `()[I` | `areturn` |
| `Object[][] matrix()` | `()[[Ljava/lang/Object;` | `areturn` |

Những ký hiệu như `I`, `J`, `V`, `L...;` và `[` thuộc **JVMS `4.3`**. Khi phân tích bytecode, **kiểu trả về được ghi trong descriptor** là nguồn để xác định opcode nào hợp lệ; kiểu của một local variable tình cờ nằm trên stack trước đó không tự cho phép dùng một opcode return khác.

Ví dụ `return 42;` trong method khai báo `long` được compiler xử lý thành giá trị `long`, có thể qua `i2l` hoặc nạp trực tiếp constant long, rồi kết thúc bằng **`lreturn`**; không thể chỉ dùng `ireturn` để trả về `long` vì source expression vốn bắt đầu là số nguyên 42.

### Vì sao method reference khác method return?

Một lời gọi `foo()` trong bytecode caller có một **symbolic method reference** (owner/name/descriptor) hoặc call site tương ứng; lệnh return chạy **ở callee** khi phương thức thực thi xong. `return` không cần chứa constant-pool index của caller, và nó cũng không chọn lại phương thức đích. Method invocation và method completion là hai bước khác nhau.

## 6. `void` method, constructor và `<clinit>`

### Java source không viết `return;` vẫn có opcode return

~~~java
static void show() {
    System.out.println("done");
}
~~~

Method `show` kết thúc khi đến hết block. Compiler phát sinh opcode `return` tương ứng để VM biết phương thức `void` đã hoàn tất.

Bạn cũng có thể viết:

~~~java
static void choose(boolean stop) {
    if (stop) {
        return;
    }
    System.out.println("continue");
}
~~~

Trong `void` method, `return;` **không có expression**. Bytecode có thể có nhiều lối dẫn tới opcode `return`, hoặc branch/goto dẫn đến một điểm return chung. **Không** có `return void;` hay `return null;` hợp lệ trong `void` method.

### Constructor: bytecode `<init>` kết thúc bằng `return`

Constructor Java không khai báo kiểu trả về:

~~~java
class Ticket {
    final int number;

    Ticket(int number) {
        this.number = number;
    }
}
~~~

Đây là class minh họa, không có `main` để chạy độc lập. Trong class file, constructor là **instance initialization method** tên `<init>` và descriptor `(I)V`. Lệnh `return` kết thúc việc thực thi **constructor**, nhưng biểu thức `new Ticket(5)` có giá trị reference do cơ chế cấp phát/invocation ở caller cung cấp.

Điểm hay nhầm: **constructor có descriptor trả `V` không có nghĩa opcode `<init>` trả về chính object**. Khi source viết `new Ticket(5)`, kết quả expression là object reference, nhưng constructor được gọi qua `invokespecial` và hoàn tất bằng `return`, không bằng `areturn`.

### Class initializer: `<clinit>` cũng dùng `return`

Compiler có thể sinh method đặc biệt `<clinit>` cho các biểu thức khởi tạo `static` và `static` blocks. Nó có descriptor `()V`, kết thúc bình thường bằng opcode `return`. Không thể khai báo hay gọi `<clinit>` trực tiếp bằng cú pháp Java thường.

Nếu một static initializer ném exception và làm class initialization thất bại, đó **không phải** một lần hoàn tất bình thường bằng `return`. Runtime áp dụng các quy tắc class initialization error riêng của JVMS/JLS.

## 7. `return` kết thúc lời gọi, còn `ret` legacy thì không

Trong bytecode lịch sử, `jsr`/`ret` từng được dùng cho **subroutine trong cùng một method**, thường liên quan cách biên dịch `finally` cũ.

~~~text
Một method:
  jsr Lcleanup     ; đẩy returnAddress đến instruction kế tiếp
  ireturn          ; SAU KHI cleanup xong, mới trả int cho method caller

Lcleanup:
  astore_2         ; lưu returnAddress vào local slot 2
  ... cleanup ...
  ret 2            ; nhảy về instruction sau jsr, trong cùng method
~~~

Đây là **sơ đồ bytecode lịch sử, không phải chương trình hoàn chỉnh để nạp vào JVM**. `ret` dùng giá trị `returnAddress` trong local slot để chuyển PC bên trong method. `ireturn` dùng một `int` trên operand stack để hoàn tất method và chuyển giá trị về caller. **Hai lệnh không cùng chức năng**, mặc dù tên đều chứa “ret/return”.

Theo **JVMS `4.9.1`**, class files với **major version ≥ 51 (Java 7+)** không được chứa `jsr`, `jsr_w` và `ret` trong `Code`. Do đó, với bytecode tạo bằng `javac` hiện đại, hãy tìm **sáu opcode return** nêu ở mục 2, không tạo ví dụ mới bằng `ret`.

## 8. `finally`: source return có thể chưa phải opcode return ngay

Một lỗi thường gặp là cho rằng cứ gặp `return expression;` trong source thì JVM lập tức thực thi `ireturn`/`areturn` ở đúng vị trí đó. Với `try/finally`, Java phải hoàn tất công việc của `finally` trước khi phương thức thực sự trở về bình thường.

Tạo file **`FinallyReturns.java`**:

~~~java
public class FinallyReturns {
    static int normal(int input) {
        try {
            System.out.println("try=" + input);
            return input * 2;
        } finally {
            System.out.println("finally=" + input);
        }
    }

    static int overrideReturn() {
        try {
            return 10;
        } finally {
            return 20; // ví dụ cảnh báo: tránh viết return trong finally
        }
    }

    static int fail() {
        try {
            System.out.println("start");
            throw new IllegalStateException("boom");
        } finally {
            System.out.println("cleanup");
        }
    }

    public static void main(String[] args) {
        System.out.println("normal=" + normal(4));
        System.out.println("overridden=" + overrideReturn());

        try {
            fail();
        } catch (IllegalStateException ex) {
            System.out.println("caught=" + ex.getMessage());
        }
    }
}
~~~

Lệnh:

~~~bash
javac -g FinallyReturns.java
java FinallyReturns
~~~

**Stdout chính xác:**

~~~text
try=4
finally=4
normal=8
overridden=20
start
cleanup
caught=boom
~~~

### Diễn giải ba nhánh

**`normal(4)`**: biểu thức `input * 2` tính ra 8; `finally` in thông điệp; cuối cùng lời gọi trả về giá trị 8. Trong bytecode, compiler có thể lưu tạm 8 vào local slot, thực thi code `finally` rồi mới dùng `ireturn`. Một câu lệnh source `return input * 2;` do đó có thể triển khai qua **nhiều opcode và nhiều BCI**.

**`overrideReturn()`**: `finally` có `return 20;`, nên kết quả `10` dự định trả từ `try` **bị thay thế** bằng 20. Trong Java, nếu `finally` hoàn tất bất thường bằng một `return` khác hoặc ném exception, lý do hoàn tất trước đó của `try` có thể bị thay thế. Đây là lý do **không nên dùng `return` trong `finally` trong code thực tế**: nó khiến kết quả hoặc exception trước đó bị che khuất. Ví dụ đưa ra để hiểu ngữ nghĩa, không khuyến nghị phong cách viết này.

**`fail()`**: ném `IllegalStateException`, chạy `finally` để in `cleanup` rồi exception truyền ra caller. Method `fail` **không hoàn tất bình thường**, không đưa `int` result nào về `main`. `main` bắt exception rồi tiếp tục chạy bình thường.

### Vì sao `javap` có thể hiển thị nhiều opcode return?

~~~bash
javap -c -p FinallyReturns
javap -v -p FinallyReturns
~~~

Trong `normal` có thể thấy nhiều nhánh và các đoạn code in `finally` ở những đường thoát khác nhau. Compiler có thể duplicate phần code `finally` hay tổ chức handler phù hợp; **không yêu cầu chỉ có một opcode `ireturn` cho mỗi method**. Trong method `overrideReturn`, compiler có thể không phát sinh một đường `ireturn` thực sự trả 10 vì `finally` luôn thay thế kết quả.

Điều quyết định liệu method **đã hoàn tất bình thường** là **opcode return cuối cùng thực thi theo đường đi cụ thể và các ràng buộc khác**, không phải số lượng từ khóa `return` bạn đếm trong Java source.

## 9. `return` khác `throw` và việc hoàn tất bất thường

Đường bình thường (normal completion) khác với **abrupt completion**:

| Tình huống | Frame callee | Caller nhận gì? |
| --- | --- | --- |
| `return;` trong method `void` | Kết thúc bình thường | Không có giá trị trả về |
| `return 5;` từ method `int` | Kết thúc bình thường | Một `int` giá trị 5 |
| `return obj;` từ method `Object` | Kết thúc bình thường | Một reference (có thể `null`) |
| Exception bị bắt và xử lý **bên trong** callee, sau đó `return` | Cuối cùng hoàn tất bình thường | Result thích hợp nếu có |
| Exception **không được xử lý** trong callee | Kết thúc bất thường | **Không có result**; exception tiếp tục tìm handler bên ngoài |
| `finally` tự ném exception | Có thể làm kết thúc bất thường dù try đã tính result | Không có result bình thường nếu exception đi ra caller |

Theo **JVMS `2.6.5`**, invocation hoàn tất bất thường **không bao giờ trả một giá trị bình thường cho caller**. Đây là điểm rất quan trọng khi đọc opcode `athrow`: nó xử lý exception, **không phải** một kiểu `return` nữa.

Một phương thức Java khai báo `int` có thể ném exception mà không thực hiện `ireturn` trên đường đó. Quy tắc compiler kiểm tra tính đầy đủ đường `return` trong source cần tính đến các đường luôn `throw`; JVM verifier kiểm tra đường bytecode riêng.

### Ví dụ Java 17+ ngắn về exception được bắt trước return

~~~java
static int parseOrDefault(String text) {
    try {
        return Integer.parseInt(text);
    } catch (NumberFormatException ex) {
        return -1;
    }
}
~~~

Đây là method minh họa nằm trong một class. Nếu parse thành công, trả `int` đã parse; nếu parse ném `NumberFormatException` và được bắt ngay trong method, method vẫn hoàn tất bình thường bằng `ireturn` với giá trị -1. Không được nói rằng “cứ gặp exception thì invocation chắc chắn abnormal”: **chỉ exception thoát ra không được xử lý trong callee** mới làm invocation đó hoàn tất bất thường.

## 10. `synchronized method`: monitor được xử lý khi return

Khi một method có modifier `synchronized`, JVM phải có ngữ nghĩa vào/ra monitor của object tương ứng:

- **Instance synchronized method** khóa monitor của receiver, tức `this`.
- **Static synchronized method** khóa monitor gắn với `Class` object đại diện class đó.
- Khi method hoàn tất bình thường, JVM xử lý việc rời monitor (hoặc cập nhật số lần tái nhập) như thể `monitorexit` thích hợp đã được thực hiện.
- Trên đường exception không được xử lý, JVM cũng phải xử lý monitor release theo quy tắc phù hợp.

Điểm kỹ thuật: **`javac` thường không tạo `monitorenter`/`monitorexit` tường minh bao quanh body của `synchronized method`**. Method được đánh dấu **`ACC_SYNCHRONIZED`**, và cơ chế invocation/return của VM xử lý monitor. Ngược lại, **`synchronized (lock) { ... }` block** trong source thường được compiler chuyển thành `monitorenter`/`monitorexit` và exception handler cần thiết trong `Code`.

Tạo file **`SynchronizedReturn.java`**:

~~~java
public class SynchronizedReturn {
    private int counter;

    public synchronized int increment() {
        counter++;
        return counter;
    }

    public static void main(String[] args) {
        SynchronizedReturn sample = new SynchronizedReturn();
        System.out.println("first=" + sample.increment());
        System.out.println("second=" + sample.increment());
    }
}
~~~

~~~bash
javac -g SynchronizedReturn.java
java SynchronizedReturn
javap -v -p SynchronizedReturn
~~~

**Stdout của chương trình:**

~~~text
first=1
second=2
~~~

Trong `javap -v -p`, hãy tìm method `increment` với **`ACC_PUBLIC, ACC_SYNCHRONIZED`**, descriptor `()I` và opcode `ireturn`. JVM đảm bảo monitor tương ứng được xử lý khi method hoàn tất; việc trả về 1/2 không khiến khóa tiếp tục bị giữ vĩnh viễn. Lệnh `javap` chạy tiếp sau chương trình sẽ in **metadata/disassembly bổ sung**, không thuộc stdout của hai lời in từ chương trình.

**Lưu ý:** Java `synchronized` là cơ chế đồng bộ thread; ví dụ chạy một thread chứng minh output dự đoán được, **không** chứng minh tính đúng trong mọi tình huống cạnh tranh. Để phân tích concurrency, cần xem Java Memory Model, quy tắc lock và tình huống nhiều thread.

## 11. Opcode, slot và descriptor: tránh nhầm ba tầng

### `max_stack`/`max_locals` không phải số result

Một `Code` attribute trong class file chứa `max_stack` và `max_locals`:

- `max_stack` cho biết operand stack có thể sâu tối đa bao nhiêu **slots** trong lúc method chạy.
- `max_locals` cho biết dung lượng local slots cho method.
- Method return có thể đẩy **một kết quả logic** sang caller nhưng kết quả ấy có thể chiếm 1 hoặc 2 operand-stack slots.

Ví dụ:

~~~text
callee: long produce()
  descriptor: ()J
  operand stack ngay trước lreturn: [long 4000000000]
  logical values: 1
  stack slots occupied by long: 2

caller: long n = produce()
  sau invoke, caller operand stack: [long 4000000000]
  rồi lstore_<n> ghi vào 2 local slots liên tiếp
~~~

Sơ đồ trên mô tả mô hình JVMS; trong `javap`, `lstore` có local slot index, còn `#N` ở `invokestatic #N` là constant-pool index. Không có liên hệ rằng lệnh `lreturn` phải trỏ tới constant pool để biết slot chứa giá trị.

### Boolean và array không cần opcode return riêng

`boolean ok()` trong source tạo descriptor `()Z` nhưng kết thúc bằng `ireturn`; `int[] data()` tạo descriptor `()[I` nhưng kết thúc bằng `areturn`. Đây là **quy tắc cấp máy ảo**; ký hiệu descriptor và loại opcode không phải tên giống hệt Java keyword.

### Return statement trong vòng lặp

~~~java
static int firstPositive(int[] values) {
    for (int value : values) {
        if (value > 0) {
            return value;
        }
    }
    return -1;
}
~~~

Đây là method minh họa, cần đặt trong class. Vòng lặp **không tạo frame mới** mỗi vòng; chỉ có một lần gọi method và một frame. Có thể có nhiều opcode `ireturn`, mỗi đường chạy đến một trong chúng. Những nhánh không kết thúc thì tiếp tục method; opcode return thực thi nào quyết định lần gọi kết thúc ở đâu.

## 12. Lỗi phổ biến khi học và đọc bytecode return

| Hiểu nhầm | Cách hiểu đúng |
| --- | --- |
| “Java có 8 kiểu primitive nên phải có 8 return opcodes.” | `boolean`, `byte`, `char`, `short` dùng cùng `ireturn` với `int`; `void`, reference và số lớn có các opcode riêng. |
| “`ireturn` luôn yêu cầu method khai báo `int`.” | Nó cũng đúng với method `boolean`, `byte`, `short`, `char`. |
| “`return` là bytecode trả `null`.” | `return` cho `void`; `null` reference được đẩy rồi trả bằng `areturn`. |
| “`areturn` chỉ trả Java Object, không trả array.” | Mảng cũng là JVM reference type, nên dùng `areturn`. |
| “`lreturn`/`dreturn` trả hai giá trị.” | Chúng trả một giá trị category 2, chiếm hai operand-stack slots. |
| “`return 5;` của Java luôn là `ireturn`.” | Tùy **method descriptor**: method `long` phải trả bằng `lreturn`. |
| “Caller luôn nhận value ở local variables ngay khi invoke xong.” | Return result được đưa lên **operand stack của caller**; caller có thể lưu vào local. |
| “Mỗi source `return` tương ứng đúng một opcode return tại cùng BCI.” | Compiler có thể sắp xếp control flow và `finally` trước khi thực sự return. |
| “`ret` là cách rút gọn của `return`.” | `ret` là legacy subroutine branch, không phải method return. |
| “Nếu callee ném exception thì cũng trả một object exception qua `areturn`.” | Uncaught exception gây **abrupt completion**, không trả value bình thường. |
| “`finally` chạy sau khi callee đã trả value cho caller.” | `finally` cần hoàn tất trước khi caller nhận normal result; bản thân `finally` có thể thay đổi kết quả/exception. |
| “`synchronized method` luôn phải có opcode `monitorexit` in trong Code.” | Monitor vào/ra cho method được quản lý theo `ACC_SYNCHRONIZED` và invocation/return semantics. |
| “Constructor `new C()` dùng `areturn` để trả object.” | `<init>` có descriptor `()V` và dùng `return`; value của biểu thức `new` do caller-side object construction logic tạo ra. |
| “Phương thức `void` kết thúc source block không có instruction return nào.” | Compiler phải tạo đường kết thúc bytecode hợp lệ bằng `return` theo ngữ nghĩa JVM. |
| “JIT inline method nghĩa là JVM bỏ qua quy tắc return.” | Tối ưu implementation có thể thay hình dạng native code nhưng phải giữ ngữ nghĩa return quan sát được. |

## 13. Bài tập tự kiểm tra

**Câu 1.** `boolean valid()` và `int answer()` có cùng opcode hoàn tất bình thường không?  
**Đáp án:** Có, đều dùng **`ireturn`** theo descriptor tương ứng.

**Câu 2.** Phương thức `long count()` có `return 2;` trong source. Opcode kết thúc là gì?  
**Đáp án:** **`lreturn`**, vì method khai báo `long`. Compiler phải tạo một giá trị long phù hợp trước khi return.

**Câu 3.** `String[] names()` và `int[] numbers()` dùng opcode gì?  
**Đáp án:** Cả hai dùng **`areturn`**, vì array là reference type.

**Câu 4.** Khi `callee()` trả `int 9` bình thường, frame caller tiếp tục với result ở đâu?  
**Đáp án:** Trên **operand stack của caller**; caller có thể dùng ngay trong phép toán hoặc ghi vào local.

**Câu 5.** `return;` trong một method `void` có đẩy `null` lên caller không?  
**Đáp án:** Không. Opcode `return` không chuyển giá trị.

**Câu 6.** Constructor `Ticket(int)` có JVM descriptor nào, và opcode cuối bình thường?  
**Đáp án:** `(I)V`, dùng **`return`**. Constructor không trả object reference bằng `areturn`.

**Câu 7.** `try { return 10; } finally { return 20; }` nhận giá trị gì ở caller?  
**Đáp án:** **20**, vì `finally` thay thế lý do hoàn tất trước đó. Không nên dùng kiểu code này trong thực tế.

**Câu 8.** Một method `int` ném exception không được bắt trong chính method. Có `ireturn` nào đưa value về caller không?  
**Đáp án:** Không, invocation hoàn tất **bất thường** và không có return value bình thường.

**Câu 9.** `ret 2` trong file Java 6 cũ có tương đương `ireturn` không?  
**Đáp án:** Không. `ret` dùng returnAddress để nhảy trong cùng method sau `jsr`. `ireturn` kết thúc method và trả một `int` cho caller.

**Câu 10.** Method `synchronized int increment()` có cần compiler chèn cặp `monitorenter`/`monitorexit` tường minh ở thân Code không?  
**Đáp án:** Không cho việc khóa ở cấp synchronized method; JVM xử lý monitor implicit nhờ `ACC_SYNCHRONIZED`, và `ireturn` tham gia đường kết thúc bình thường.

## 14. Tài liệu chính thức

- [JVMS Java SE 25 — `2.6` Frames](https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-2.html#jvms-2.6): frame, local variables, operand stack và việc caller/callee chuyển quyền điều khiển.
- [JVMS Java SE 25 — `2.6.4` Normal Method Invocation Completion](https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-2.html#jvms-2.6.4): kết thúc bình thường, khôi phục caller, trả giá trị.
- [JVMS Java SE 25 — `2.6.5` Abrupt Method Invocation Completion](https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-2.html#jvms-2.6.5): kết thúc bất thường không có normal result.
- [JVMS Java SE 25 — `2.11.8` Method Invocation and Return Instructions](https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-2.html#jvms-2.11.8): ánh xạ kiểu dữ liệu sang các opcode return.
- [JVMS Java SE 25 — `6.5` JVM Instructions](https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-6.html): mục `return`, `ireturn`, `lreturn`, `freturn`, `dreturn`, `areturn` và ràng buộc synchronized monitor.
- [JVMS Java SE 25 — `4.3` Descriptors](https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-4.html#jvms-4.3): các ký hiệu `V`, `I`, `J`, `F`, `D`, `L...;`, `[` và dạng method descriptor.
- [JVMS Java SE 25 — `4.9.2` Structural Constraints](https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-4.html#jvms-4.9.2): opcode return phải đúng kiểu trả về đã khai báo.
- [JLS Java SE 25 — `14.17` The return Statement](https://docs.oracle.com/javase/specs/jls/se25/html/jls-14.html#jls-14.17): `return` ở Java source, kể cả khi có `try/finally`.
- [JLS Java SE 25 — `14.20.2` Execution of try-finally](https://docs.oracle.com/javase/specs/jls/se25/html/jls-14.html#jls-14.20.2): cách `finally` ảnh hưởng normal/abrupt completion.
- [Oracle JDK 25 — `javap`](https://docs.oracle.com/en/java/javase/25/docs/specs/man/javap.html): dùng `javap -c -s -v -p` để xem opcode return, descriptor và metadata.

**Ghi nhớ khi đọc bytecode:** nhìn **return descriptor** của method trước, sau đó xem **opcode return** trên từng nhánh. Một lời gọi chỉ hoàn tất bình thường khi thực thi đúng lệnh return và các xử lý cần thiết hoàn tất; **value đi về operand stack caller**, còn exception không được xử lý làm invocation hoàn tất bất thường và **không tạo result bình thường**.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="stack-frames-and-calls-008">athrow, bảng xử lý ngoại lệ và tháo frame khi lời gọi thất bại</a>

<details>
<summary>Xem chi tiết</summary>

# `athrow`, bảng xử lý ngoại lệ và tháo frame khi lời gọi thất bại

Java không xử lý ngoại lệ như một giá trị trả về bình thường. Khi một phương thức gặp lỗi và không bắt được, JVM loại frame của lời gọi đó khỏi call stack, tìm tiếp handler phù hợp trong frame của caller. Cơ chế kết hợp **`athrow`**, **`Code.exception_table`** và **frame unwinding** bảo đảm ngoại lệ có thể đi qua nhiều phương thức cho tới khi được xử lý hoặc thread kết thúc.

Bài này thuộc chủ đề **Frame và lời gọi**, giải thích các quy tắc bytecode, cách `try/catch/finally` và `try-with-resources` được biểu diễn, sự khác nhau giữa exceptional completion và normal return, cũng như cách đọc `javap`. Ví dụ chạy được bằng **Java 17+**. Chi tiết class-file và hành vi JVM dựa trên JVMS Java SE 17.

## 1. Phân biệt normal completion với abrupt completion

| Con đường | Điều xảy ra ở callee | Caller nhận gì? |
| --- | --- | --- |
| **Bình thường** | Lệnh `ireturn`, `lreturn`, `freturn`, `dreturn`, `areturn` hoặc `return` hoàn tất lời gọi | Non-`void` có giá trị trả về trên operand stack caller; `void` không có giá trị |
| **Bất thường** | Có exception được ném và callee không có handler phù hợp | **Không có normal return value**; ngoại lệ lan lên caller và được tìm handler tiếp |

Ví dụ:

```java
static int divide(int x, int y) {
    return x / y;
}
```

Với `divide(10, 2)`, phép chia nguyên trả `5`. Với `divide(10, 0)`, opcode chia nguyên `idiv` gây `ArithmeticException`; `ireturn` không hoàn thành, nên caller không nhận số nguyên `0` hay `null` giả làm kết quả.

**Không phải mọi exception đều phát sinh từ từ khóa `throw` trong mã nguồn.** Các instruction như `idiv`, `aaload`, `getfield`, `invokevirtual`, những thao tác truy cập mảng hoặc liên kết lớp có thể gây exception theo quy tắc runtime. Cả exception tường minh và ngầm đều có thể được bắt bởi `exception_table`.

```text
main frame
   └── caller frame (invoke callee)
         └── callee frame
               ├── return bình thường → caller tiếp tục với kết quả
               └── ngoại lệ E
                    ├── handler ngay trong callee → chạy catch tương ứng
                    └── không có handler
                           ↓
                       pop callee frame
                           ↓
                   tìm handler ở caller, tại vị trí invoke
                           ↓
              bắt được → chạy handler | không → pop caller
```

## 2. Opcode `athrow` là gì?

`athrow` có opcode **191** (hexadecimal **`0xbf`**), dùng để ném một object thuộc lớp `java.lang.Throwable` hoặc subclass, chẳng hạn `Exception`, `RuntimeException` hoặc `Error`.

Trước `athrow`, operand stack của frame đang có một giá trị reference tới exception object ở đỉnh:

```text
operand stack trước: [..., exceptionRef]
                           │
                           ▼
                         athrow
                           │
                tìm handler hoặc unwind
```

Nếu ném trong Java:

```java
static void fail() {
    throw new IllegalStateException("bad state");
}
```

Bytecode do `javac` sinh thường chứa mẫu:

```text
new           java/lang/IllegalStateException
dup
ldc           "bad state"
invokespecial java/lang/IllegalStateException.<init>:(Ljava/lang/String;)V
athrow
```

`new` tạo object chưa hoàn tất initialization theo JVM model; `dup` giữ reference trong lúc constructor nhận receiver qua `invokespecial`; cuối cùng `athrow` dùng reference đã được khởi tạo. Đây là trích đoạn **minh họa opcode**, vì output `javap -c` có thêm offset/constant-pool indexes.

### 2.1. `athrow` khác `areturn`

- `areturn` hoàn tất phương thức **bình thường**, trả một reference đúng return descriptor cho caller.
- `athrow` làm phương thức chuyển sang **exceptional control flow**, đi tìm handler; không dùng return descriptor và không trả exception object như một normal result.
- `athrow` có thể ném `Error`; JVM không có opcode riêng tên `throwerror`.
- Checked và unchecked exceptions đều có thể được ném qua cùng cơ chế bytecode; khác biệt checked/unchecked nằm chủ yếu ở quy tắc biên dịch Java.

### 2.2. Nếu operand của `athrow` là `null`?

`athrow` với `objectref = null` sẽ ném **`NullPointerException`**, không phải ném một “exception không có đối tượng”.

```java
static void throwNull() {
    RuntimeException problem = null;
    throw problem;
}
```

Nếu caller catch `NullPointerException` bao quanh lời gọi thì có thể xử lý. Đoạn phương thức trên là ví dụ minh họa; chương trình đầy đủ ở mục 7.

### 2.3. Có exception mà bytecode không có `athrow` ngay tại vị trí lỗi

Trong `return 100 / value;`, phép `idiv` với `value = 0` làm phát sinh exception theo semantics của `idiv`. Không cần lệnh `athrow` tường minh tiếp sau `idiv`. Từ thời điểm đó JVM vẫn tìm handler bằng cùng cơ chế.

Ngược lại, một `catch` có thể `throw ex;`, thường sinh `aload` rồi `athrow` để **rethrow** cùng reference, không nhất thiết tạo một exception object mới.

## 3. Cấu trúc của `Code.exception_table`

Bytecode Java của một phương thức nằm trong **`Code` attribute** của `method_info`. Ngoài `code[]`, `max_stack`, `max_locals`, `Code` còn có `exception_table_length` và mảng **`exception_table[]`**.

Một entry gồm bốn trường `u2`:

```text
exception_table entry
├─ start_pc     // đầu vùng code được bảo vệ
├─ end_pc       // điểm kết thúc mở của vùng
├─ handler_pc   // offset bắt đầu handler
└─ catch_type   // class reference trong constant pool, hoặc 0
```

| Trường | Ý nghĩa chính xác |
| --- | --- |
| **`start_pc`** | Offset instruction đầu tiên có thể được handler áp dụng, **bao gồm** |
| **`end_pc`** | Offset giới hạn cuối, **không bao gồm** |
| **`handler_pc`** | Offset của instruction bắt đầu code handler |
| **`catch_type`** | Constant-pool class index để so kiểu exception, hoặc **0** để match mọi Throwable |

Vùng được bảo vệ là **`[start_pc, end_pc)`**. Nếu `start_pc=2, end_pc=10` thì exception tại opcode bắt đầu ở offset **2 tới trước 10** có thể được entry xử lý; tại opcode offset **10** thì không do entry đó.

`handler_pc` phải trỏ tới **đầu instruction** hợp lệ trong cùng `code[]`, không được trỏ vào byte operand ở giữa instruction. `end_pc` có thể trỏ tới đầu instruction hoặc đúng `code_length` theo ràng buộc của class-file structure.

### 3.1. Cách đọc bảng từ `javap`

Giả sử công cụ hiển thị mẫu:

```text
Exception table:
   from    to  target type
       0     4       5 Class java/lang/NumberFormatException
```

- `from 0` là `start_pc`.
- `to 4` là `end_pc` **exclusive**.
- `target 5` là `handler_pc`.
- Cột `type` là class được catch qua `catch_type`.

Nếu `invokestatic` ở offset 1 gọi phương thức ném `NumberFormatException`, entry có thể match vì 1 thuộc `[0,4)`. Không quan trọng exception có bắt nguồn từ bytecode offset nào của **callee**; khi lan về **caller**, vị trí phải kiểm là offset của **lệnh gọi trong caller**.

### 3.2. `catch_type = 0` không phải kiểu `Object`

Giá trị `0` là sentinel **catch-all**: match bất kỳ throwable nào đang được xử lý tại pc thuộc vùng bảo vệ. `javap` thường in `any`.

Nó **khác**:

- `catch (Throwable t)`: handler bắt type cụ thể `java/lang/Throwable`, thường có non-zero `catch_type` trỏ tới constant pool.
- `catch (Exception e)`: không bắt các loại `Error` chỉ kế thừa `Throwable`.
- `catch (RuntimeException e)`: chỉ bắt loại RuntimeException và subclasses.

`catch_type=0` thường dùng trong bytecode cleanup như `finally` hoặc các đường xử lý monitor. Không thể kết luận một method “nuốt mọi exception” chỉ vì thấy entry `any`: handler có thể cleanup xong **`athrow` lại**.

### 3.3. `Exceptions` attribute khác `exception_table`

`void work() throws IOException` khai báo một phần hợp đồng checked exception ở Java source. Class file có thể lưu loại khai báo qua **`Exceptions` attribute** của method.

`exception_table` lại là **bảng handler thực sự** của `Code`, mô tả runtime catch/cleanup. Method chỉ khai báo `throws` có thể **không có** exception table; method không khai báo `throws` vẫn có thể có try/catch/finally và bảng handler.

## 4. Quy tắc tìm handler: thứ tự và kiểu ngoại lệ

JVM tìm exception handler trong frame hiện tại theo thuật toán:

1. Xác định offset `pc` của instruction nơi exception phát sinh (hoặc vị trí invoke trong frame caller khi exception đã lan lên).
2. Duyệt từng entry `exception_table[]` **theo thứ tự ghi trong bảng**.
3. Kiểm tra `start_pc <= pc < end_pc`.
4. Kiểm tra type: exception là instance của class trong `catch_type` hoặc subclass; `catch_type=0` luôn match type.
5. Chọn **entry đầu tiên thỏa cả vùng và type**. Đặt pc tới `handler_pc`.
6. Không entry nào phù hợp → phương thức kết thúc bất thường, frame bị tháo và tìm ở caller.

### 4.1. JVM không tự chọn “handler hẹp nhất”

Giả sử bytecode của một phương thức có entries cùng vùng:

```text
from to target type
0    15  25     Class java/lang/RuntimeException
0    15  35     Class java/lang/IllegalArgumentException
```

Nếu ném `IllegalArgumentException` ở pc 9, JVM chọn **target 25**, vì RuntimeException ở entry đầu tiên đã match. Nó **không tự bỏ qua** handler đứng trước để tìm subtype cụ thể hơn.

Compiler Java source kiểm tra các catch clauses reachable: đặt catch rộng trước catch hẹp cùng try có thể làm `javac` báo lỗi. Nhưng khi đọc **class file**, quy tắc JVM vẫn là **entry đầu match**; bytecode generator phải lưu đúng thứ tự để không đổi ngữ nghĩa.

### 4.2. Có nhiều vùng bảo vệ chồng nhau thì sao?

Một method có nested try/catch/finally có thể có nhiều entry `[start_pc,end_pc)` chồng lấn. JVM **không** chọn vùng ngắn nhất bằng một thuật toán độc lập. Nó duyệt bảng theo thứ tự và chọn entry đầu tiên match.

Đó là lý do tự ý **sort exception table theo offset hoặc theo class name** trong bytecode instrumentation có thể làm thay đổi hành vi. Nếu handler đang chạy lại ném exception, JVM tra bảng một lần nữa theo pc mới của handler.

## 5. Stack của handler có trạng thái ra sao?

Khi tìm được handler phù hợp:

- JVM đặt pc vào `handler_pc`.
- **Xóa toàn bộ các giá trị cũ trong operand stack** của frame xử lý.
- Đẩy **một reference đến chính exception object** đang ném vào operand stack.
- Chạy instruction đầu của handler.

```text
Trước khi exception xảy ra:
  operand stack: [..., temporary1, temporary2, ...]

Khi chuyển đến handler:
  operand stack: [exceptionRef]      // một phần tử logic

Handler thông dụng:
  astore_1                           // lưu exceptionRef vào local 1
  ...                                // xử lý, return hoặc rethrow
```

**Không có normal return value** được đẩy vào caller stack khi exception lan qua callee. Nếu ở frame caller có handler bắt ngoại lệ, operand stack **của frame caller tại handler** được xóa rồi nhận một reference ngoại lệ.

Local-variable array của **frame đang xử lý** vẫn là local array của nó và phải đáp ứng verifier/StackMapTable. Còn local variables của **các frame đã unwind** không thể được truy cập từ handler của caller.

### 5.1. Vì sao handler hay bắt đầu bằng `astore`?

Java source `catch (IllegalArgumentException e)` cần dùng biến `e`. Vì khi vào handler operand stack chứa exception reference, compiler thường dùng `astore` để đưa reference vào một local slot. Nhưng `astore_1` chỉ là ví dụ; index khác tùy layout. Một cleanup handler cũng có thể lưu exception tạm, làm việc khác rồi load lại cho `athrow`.

## 6. Frame unwinding xuyên qua lời gọi phương thức

Xét chuỗi `top() → middle() → leaf()`:

```java
static int leaf() {
    throw new IllegalArgumentException("negative");
}

static int middle() {
    return leaf();
}

static void top() {
    try {
        System.out.println(middle());
    } catch (IllegalArgumentException ex) {
        System.out.println("handled=" + ex.getMessage());
    }
}
```

Trình tự ở JVM:

```text
1. leaf() chạy, athrow E.
2. Tìm exception_table của leaf: không có handler phù hợp.
3. Pop frame leaf, không trả int.
4. Phục hồi frame middle ở vị trí gọi leaf().
5. Tìm exception_table của middle cho pc của lệnh invoke leaf.
6. Không có handler → pop frame middle, vẫn không trả int.
7. Phục hồi frame top ở vị trí gọi middle().
8. Tìm exception_table của top cho pc của lệnh invoke middle.
9. Có handler catch IllegalArgumentException → top vào handler.
10. Operand stack top tại handler = [E]; handler in message.
```

**Điểm tinh tế:** khi tìm handler của `top`, JVM dùng offset của **instruction gọi `middle` trong `top`**, không phải offset `athrow` của `leaf`. Mỗi method có code array riêng; các offset không dùng chung.

Nếu không frame nào có handler, thread kết thúc do uncaught exception. Uncaught exception handler của thread hoặc runtime có thể báo lỗi. Các thread khác **không nhất thiết cùng chết**, và một JVM có non-daemon threads khác có thể tiếp tục hoạt động.

### 6.1. Stack trace khác với call stack còn sống

Stack trace trên exception có thể ghi lại thông tin những phương thức liên quan tới lúc lỗi xảy ra. Nhưng sau khi unwind, các frame callee đã bị loại khỏi call stack đang chạy. Những dòng stack trace là thông tin chẩn đoán, **không** giữ local variables sống để handler ở caller đọc lại.

## 7. Chương trình Java 17+ đầy đủ: return, `athrow` ngầm và tường minh

Tạo file **`ExceptionFlowDemo.java`**:

```java
public class ExceptionFlowDemo {

    static int leaf(int value) {
        if (value < 0) {
            throw new IllegalArgumentException("negative");
        }
        return 100 / value;
    }

    static int middle(int value) {
        try {
            return leaf(value);
        } finally {
            System.out.println("middle finally (" + value + ")");
        }
    }

    static void run(int value) {
        try {
            int result = middle(value);
            System.out.println("result=" + result);
        } catch (IllegalArgumentException ex) {
            System.out.println("argument=" + ex.getMessage());
        } catch (ArithmeticException ex) {
            System.out.println("arithmetic=" + ex.getMessage());
        }
    }

    public static void main(String[] args) {
        run(2);
        run(-1);
        run(0);
    }
}
```

Chạy:

```bash
javac --release 17 ExceptionFlowDemo.java
java ExceptionFlowDemo
```

**Stdout chính xác:**

```text
middle finally (2)
result=50
middle finally (-1)
argument=negative
middle finally (0)
arithmetic=/ by zero
```

Ba đường thực thi:

| Đầu vào | `leaf` | `middle` | `run` |
| --- | --- | --- | --- |
| `2` | Trả `50` qua `ireturn` | Chạy `finally` rồi trả `50` | In `result=50` |
| `-1` | Tạo IllegalArgumentException rồi `athrow` | Chạy `finally` và ném tiếp | Catch IllegalArgumentException, in message |
| `0` | `idiv` ném ArithmeticException ngầm | Chạy `finally` và ném tiếp | Catch ArithmeticException, in `/ by zero` |

Dòng `middle finally (...)` xuất hiện cả trong **normal path** lẫn **exceptional path**. Khi ném lỗi, `middle` không trả `int` giả rồi “báo lỗi song song”; nó **không normal-complete** và ngoại lệ tìm handler ở `run`.

### 7.1. Quan sát bytecode bằng `javap`

```bash
javap -c -p ExceptionFlowDemo
javap -v -p ExceptionFlowDemo
```

Tìm từng nơi:

- `leaf` có `new`, `dup`, `invokespecial`, **`athrow`** ở nhánh âm, và `idiv` ở đường chia.
- `middle` có code đảm bảo `finally` chạy cả hai đường. Compiler thường sinh **catch-all handler** cùng code cleanup và một `athrow` để rethrow.
- `run` có entries trong **Exception table** cho `IllegalArgumentException` và `ArithmeticException`.
- Code của `catch` thường có `astore` lưu exception reference trước khi gọi `getMessage()`.
- `main` gọi `run` ba lần.

Một bảng mẫu minh họa cấu trúc (offset không lấy từ bản build cố định):

```text
Exception table:
  from   to   target   type
     0   12       20   Class java/lang/IllegalArgumentException
     0   12       35   Class java/lang/ArithmeticException
```

Không dùng các offset ví dụ để debug file thật. `javap` từ class do bạn biên dịch mới cho vị trí chính xác.

## 8. Cách `try/catch/finally` được compiler hạ xuống bytecode

### 8.1. `catch` không phải phương thức riêng

`catch (NumberFormatException e)` được hạ thành một hoặc nhiều entry trong `exception_table` và **bytecode handler** trong cùng `Code` attribute của method chứa try/catch. Handler không phải một method tên `catch`.

```java
static int parse(String text) {
    try {
        return Integer.parseInt(text);
    } catch (NumberFormatException ex) {
        return -1;
    }
}
```

Cấu trúc ý tưởng:

```text
try code:
   aload_0
   invokestatic Integer.parseInt
   ireturn
handler:
   astore_1
   iconst_m1
   ireturn
exception_table:
   [try code range) -> handler, catch_type NumberFormatException
```

### 8.2. `finally` không phải opcode JVM

Java compiler sinh đủ code để `finally` chạy theo đường rời `try`/`catch`:

- Khi return bình thường, chạy cleanup rồi đưa normal value về caller.
- Khi ném exception, handler có thể lưu exception tạm, chạy cleanup rồi **`athrow` lại**.
- Với `break`, `continue` hay nhiều exits, compiler tổ chức branches và có thể nhân bản cleanup code.

Ví dụ mô hình catch-all:

```text
Exception table:
  from  to  target  type
     0  10      18  any

18: astore_2
19: ... mã cleanup ...
24: aload_2
25: athrow
```

`any` thường biểu thị `catch_type=0`. Không nên nhìn mỗi chữ `any` rồi kết luận method đã nuốt hết lỗi: ở ví dụ handler **ném lại exception sau cleanup**.

**Không giả định compiler hiện đại luôn dùng `jsr` / `ret` cho finally.** Đó là cơ chế lịch sử, không phải sơ đồ mặc định của `javac` Java 17.

### 8.3. Ngoại lệ hoặc `return` trong chính `finally`

Java `finally` có thể làm thay đổi con đường hoàn tất:

```java
static int unsafe() {
    try {
        throw new IllegalStateException("first");
    } finally {
        return 99;
    }
}
```

Trong trường hợp này, `return` của `finally` có thể **thay thế** việc ném ngoại lệ từ `try`, khiến `unsafe()` trả `99` bình thường. Đây là lý do nên tránh `return` trong `finally`: nó che giấu exception cần điều tra.

Nếu cleanup tự ném exception khác, error đó cũng có thể thay thế exception đang truyền theo quy tắc Java thông thường. Try-with-resources xử lý tình huống đồng thời hai exception bằng **suppressed exceptions**, khác với `finally` tự viết đơn giản.

## 9. `try-with-resources` và `getSuppressed()`

Tạo file **`SuppressedDemo.java`**:

```java
public class SuppressedDemo {
    static final class Resource implements AutoCloseable {
        @Override
        public void close() {
            throw new IllegalStateException("close failed");
        }
    }

    static void work() throws Exception {
        try (Resource resource = new Resource()) {
            throw new Exception("body failed");
        }
    }

    public static void main(String[] args) {
        try {
            work();
        } catch (Exception ex) {
            System.out.println("primary=" + ex.getMessage());
            System.out.println("suppressed=" + ex.getSuppressed().length);
            System.out.println("suppressed-message=" +
                    ex.getSuppressed()[0].getMessage());
        }
    }
}
```

Biên dịch và chạy:

```bash
javac --release 17 SuppressedDemo.java
java SuppressedDemo
```

Stdout:

```text
primary=body failed
suppressed=1
suppressed-message=close failed
```

Vì thân `try` ném lỗi `body failed` trước và `close()` cũng ném `close failed`, Java giữ lỗi body làm **primary exception** rồi gắn lỗi close dưới dạng **suppressed exception**. Compiler có thể dùng handlers, locals và lời gọi `Throwable.addSuppressed` để bảo đảm hành vi.

`getCause()` và `getSuppressed()` là hai khái niệm khác nhau: cause mô tả chuỗi nguyên nhân, còn suppressed lưu những lỗi thứ cấp xảy ra trong cleanup hoặc hoàn cảnh phù hợp.

**Thực hành:** không dựa vào GC để đóng file/socket/DB connection; dùng `try-with-resources` hoặc cleanup rõ ràng khi tài nguyên cần được đóng theo vòng đời.

## 10. `synchronized` và việc nhả monitor khi ném lỗi

Với **synchronized instance/static method**, JVM quản lý monitor khi method invocation bắt đầu. Khi method kết thúc bất thường, monitor đã được vào cho lời gọi này phải được thoát theo quy tắc của JVM.

Với khối `synchronized (lock)`, compiler thường dùng `monitorenter` / `monitorexit` và handler để thoát monitor trên đường exception:

```java
static void guarded(Object lock) {
    synchronized (lock) {
        throw new IllegalStateException("inside lock");
    }
}
```

Dù câu lệnh trong block ném lỗi, Java vẫn yêu cầu rời synchronized block đúng cách. Một bytecode generator tự chèn `monitorenter` mà quên đường `monitorexit` khi exception xảy ra có thể phá vỡ tính hợp lệ/đúng đắn.

**Không đồng nhất** monitor management với việc `finally` tự đóng mọi loại tài nguyên. Monitor có quy tắc JVM riêng; file handle và network socket cần thao tác đóng rõ ràng.

## 11. Checked và unchecked exceptions đều dùng cùng đường truyền runtime

Java compiler kiểm tra checked exceptions như `IOException` theo quy tắc `catch` hoặc `throws`. Nhưng lúc JVM chạy:

- `athrow` không có một opcode cho checked và opcode khác cho unchecked.
- Handler so **exception object type** với `catch_type` và vị trí `pc`.
- `Exceptions` attribute từ `throws` khác `exception_table` dùng để bắt.
- `Error` và `RuntimeException` cũng có thể được throw/catch theo JVM, dù thiết kế ứng dụng thường không nên catch `Throwable` bao quát.

Ví dụ:

```java
static void checked() throws java.io.IOException {
    throw new java.io.IOException("cannot read");
}
```

Lệnh `throws` giúp kiểm tra ở source/caller, trong khi đường ném thực tế của body vẫn kết thúc bằng `athrow`. Không có typed return đặc biệt cho checked exception.

### 11.1. Thư viện bất đồng bộ có thể đóng gói lỗi

Nếu task trên thread khác ném lỗi, `Future.get()` có thể ném `ExecutionException` bao bọc lỗi task; `CompletableFuture.join()` có thể ném `CompletionException`. Đây là **cơ chế thư viện truyền trạng thái lỗi giữa các task/thread**, không có nghĩa `athrow` trực tiếp vượt từ stack của thread A sang stack thread B.

Mỗi thread có call stack riêng; frame unwinding xử lý dọc chuỗi lời gọi của chính thread hiện tại.

## 12. Đọc stack trace và exception handler đúng công cụ

- **`javap -c -v ClassName`**: xem bytecode, `Code`, `Exception table`, type, handler offsets và `StackMapTable`.
- **Stack trace**: thông tin call chain khi Throwable nhận stack trace, không hiển thị trực tiếp handler table hoặc local-variable array.
- **Debugger**: có thể dừng tại exception throw/catch, xem frame và biến còn sống tùy debug info.
- **`javac -g`**: cung cấp thêm thông tin debug như `LocalVariableTable` giúp tìm tên biến; không biến `exception_table` thành source-level data.

Ví dụ stack trace **chỉ minh họa**:

```text
java.lang.IllegalArgumentException: negative
    at ExceptionFlowDemo.leaf(ExceptionFlowDemo.java:6)
    at ExceptionFlowDemo.middle(ExceptionFlowDemo.java:12)
    at ExceptionFlowDemo.run(ExceptionFlowDemo.java:21)
```

Chương trình ví dụ ở mục 7 **bắt được exception**, nên không tự in stack trace này ra stdout. Các số dòng không nhất thiết trùng với file của bạn.

## 13. Khi chỉnh bytecode, lỗi handler thường xuất phát ở đâu?

Nếu viết ASM/Byte Buddy hoặc instrument class files, cần giữ những bất biến:

1. **`start_pc < end_pc`**, cả `start_pc` và `handler_pc` chỉ vào đầu instruction hợp lệ, `end_pc` chỉ vào đầu instruction hợp lệ hoặc bằng `code_length`.
2. **`catch_type` hợp lệ**: 0 hoặc constant-pool index đến `CONSTANT_Class_info` phù hợp.
3. **Thứ tự bảng giữ đúng semantics**: entry đầu tiên match sẽ thắng; đổi thứ tự có thể thay đổi handler được chạy.
4. **Stack tại handler** có một reference Throwable phù hợp với verification state. Handler không được suy rằng operand stack cũ còn tồn tại.
5. **`StackMapTable` và locals** phải hợp lệ tại đường điều khiển hội tụ theo class-file version.
6. **Monitor cleanup** của synchronized blocks và logic `finally` phải hoạt động trên mọi đường thoát được Java quy định.
7. **Không dùng typed return sai** để che exception hoặc tạo giá trị normal giả.

Vi phạm có thể dẫn tới `ClassFormatError`, `VerifyError` hoặc lỗi khi liên kết/thực thi tùy tính chất. Không có một thông điệp lỗi cố định cho mọi exception table sai.

### 13.1. Ví dụ lỗi `end_pc` exclusive

```text
bytecode offsets:
  0 ...  first protected instruction
  8 ...  last protected instruction
 11 ...  next instruction (outside try)
```

Nếu muốn bảo vệ instruction ở 8 nhưng không bảo vệ instruction ở 11, dùng `start_pc=0, end_pc=11`. Dùng `end_pc=8` lại **bỏ chính instruction ở 8** khỏi vùng bảo vệ.

### 13.2. Tháo frame không có nghĩa mất mọi đối tượng heap

Frame bị unwind không còn hoạt động; local refs trong frame đó không còn là nguồn giữ object theo trạng thái logic. Nhưng object trên heap **có thể vẫn reachable** từ caller, static fields, collections, exception object hoặc nguồn khác. GC không bắt buộc chạy ngay khi frame bị tháo.

Đừng lẫn **frame unwinding** với **garbage collection** hoặc cleanup tài nguyên hệ điều hành.

## 14. Những nhầm lẫn thường gặp

1. **“`athrow` giống `areturn` nhưng trả exception.”** Sai. `athrow` kích hoạt exceptional flow, `areturn` là normal return.
2. **“Mọi exception trong Java đều có chữ `throw` ở đúng dòng lỗi.”** Sai. Runtime có thể ném từ opcode như `idiv` hoặc field access.
3. **“Bảng `exception_table` chứa danh sách exception ghi ở `throws`.”** Sai. `throws` và runtime handler table khác mục đích.
4. **“`end_pc` bao gồm bytecode ở đúng offset đó.”** Sai. Vùng handler là `[start_pc,end_pc)`.
5. **“JVM chọn catch subtype đặc hiệu nhất.”** Sai. Nó tìm **entry đầu tiên** thỏa cả range và type.
6. **“`catch_type=0` là type `Object`.”** Sai. Đây là catch-all sentinel.
7. **“Handler nhận nguyên operand stack ngay trước khi ném.”** Sai. Stack được xóa, chỉ còn một exception reference.
8. **“Không có catch trong callee thì caller nhận `null`.”** Sai. Không có normal return value; ngoại lệ lan tới caller.
9. **“Unwinding luôn tháo hết mọi frame.”** Sai. Dừng khi tìm được handler hợp lệ.
10. **“`finally` là một bytecode instruction.”** Sai. Compiler sinh các đường code và handlers phù hợp.
11. **“`try-with-resources` luôn thay primary exception bằng lỗi `close`.”** Sai. Trong nhiều trường hợp close exception được suppressed.
12. **“Exception không bắt ở một worker luôn giết toàn JVM.”** Sai. Các thread có thể tiếp tục theo vòng đời độc lập.
13. **“Unwind tự đóng mọi stream và socket.”** Sai. Dùng explicit cleanup/try-with-resources.
14. **“Exception object bị tạo lại mỗi khi vượt một frame.”** Sai. Rethrow có thể giữ cùng reference; wrapper exception mới là lựa chọn của code.
15. **“Stack trace là ảnh chụp các live frames hiện có mãi sau khi catch.”** Sai. Nó là thông tin chẩn đoán; nhiều frames đã unwind.

## 15. Tự kiểm tra có đáp án

**Câu 1.** Entry có `start_pc=3, end_pc=10, handler_pc=20`. Exception ở bytecode offset 10 có match entry này không?

**Đáp án:** Không; `end_pc` **exclusive**. Exception ở offset 9 có thể match nếu type phù hợp.

**Câu 2.** Handler `RuntimeException` đứng trước handler `IllegalArgumentException` trong cùng range. Nếu ném `IllegalArgumentException` thì handler nào được chọn?

**Đáp án:** Entry `RuntimeException` đứng trước, vì `IllegalArgumentException` kế thừa `RuntimeException`.

**Câu 3.** Lệnh `athrow` gặp `null` thì sao?

**Đáp án:** JVM ném `NullPointerException`; một handler phù hợp có thể bắt nó.

**Câu 4.** Khi bắt exception ở caller, operand stack của caller tại handler chứa gì?

**Đáp án:** Một exception reference; những giá trị operand khác bị xóa.

**Câu 5.** `leaf` ném ở offset 30, còn caller `top` gọi `leaf` ở offset 5. Handler table của `top` dùng pc nào?

**Đáp án:** Pc của **lệnh invoke trong `top`**, tức 5.

**Câu 6.** Vì sao ví dụ `middle` in dòng `finally` kể cả khi `leaf` ném lỗi?

**Đáp án:** Compiler dùng các nhánh code/handler để thực hiện finally trên cả normal và exceptional exits theo ngữ nghĩa Java, rồi tiếp tục return hoặc rethrow.

**Câu 7.** Trong `SuppressedDemo`, lỗi nào là primary, lỗi nào suppressed?

**Đáp án:** `body failed` là primary; `close failed` là suppressed.

**Câu 8.** Thấy một `exception_table` có `type any`. Có thể kết luận phương thức đã nuốt mọi lỗi?

**Đáp án:** Không. Handler có thể cleanup và thực hiện `athrow` rethrow; cần đọc chính bytecode tại `handler_pc`.

**Câu 9.** Nếu exception không bắt được trong thread xử lý request, mọi thread khác có bắt buộc chết theo không?

**Đáp án:** Không; chính thread đó có thể kết thúc bất thường, còn các thread khác và tiến trình phụ thuộc lifecycle của ứng dụng/runtime.

## 16. Tổng hợp ngắn bằng luồng điều khiển

```text
instruction phát sinh Throwable E
           │
           ▼
   athrow hoặc implicit exception
           │
           ▼
tra exception_table của current frame:
  [start_pc, end_pc) chứa pc?
  catch_type match type E?
  lấy entry ĐẦU TIÊN thỏa
           │
     ┌─────┴─────────┐
     │ Có handler    │ Không
     ▼               ▼
handler_pc         unwind frame
stack = [E]        không normal return
chạy handler       phục hồi caller tại invoke
                   └─ tra handler ở caller, lặp tiếp
```

**Ghi nhớ:** `athrow` dùng để ném đối tượng `Throwable`; bảng `exception_table` cho biết **pc nào được handler nào bảo vệ** và **type nào được phép bắt**. Khi match, operand stack được thiết lập với **một exception reference**. Khi không match, JVM tháo frame và tìm tiếp ở caller **không qua typed return**. `finally`, monitor cleanup và try-with-resources phối hợp với cơ chế này để thực thi đúng ngữ nghĩa Java.

### Tài liệu chuẩn

- [JVMS Java SE 17 — Exceptions (`2.10`)](https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-2.html#jvms-2.10): tìm handler, ưu tiên theo thứ tự, tháo frame.
- [JVMS Java SE 17 — Frames and abrupt invocation completion (`2.6`)](https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-2.html#jvms-2.6).
- [JVMS Java SE 17 — `athrow` (`6.5`)](https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-6.html#jvms-6.5.athrow).
- [JVMS Java SE 17 — `Code` attribute và `exception_table` (`4.7.3`)](https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-4.html#jvms-4.7.3).
- [JVMS Java SE 17 — Exception Handling (`3.12`)](https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-3.html#jvms-3.12).
- [JLS Java SE 17 — Exceptions (`11`)](https://docs.oracle.com/javase/specs/jls/se17/html/jls-11.html).
- [JLS Java SE 17 — `try`, `catch`, `finally` và try-with-resources (`14.20`)](https://docs.oracle.com/javase/specs/jls/se17/html/jls-14.html#jls-14.20).
- [JDK 17 `javap` documentation](https://docs.oracle.com/en/java/javase/17/docs/specs/man/javap.html).


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="stack-frames-and-calls-009">Ánh xạ lambda và nối chuỗi xuống bytecode theo phiên bản trình biên dịch</a>

<details>
<summary>Xem chi tiết</summary>

# Ánh xạ lambda và nối chuỗi xuống bytecode theo phiên bản trình biên dịch

Cùng một source Java có thể sinh ra **bytecode khác nhau** tùy phiên bản `javac`, `--release`, công cụ build và chiến lược của compiler. Hai ví dụ rất điển hình là biểu thức **lambda/method reference** và phép **nối chuỗi bằng toán tử `+`**. Chúng cho thấy sự khác nhau giữa:

1. **Ngữ nghĩa source Java**: đoạn code phải cho kết quả gì theo Java Language Specification (JLS).
2. **Cách compiler hạ source xuống bytecode** (*lowering/desugaring*): dùng instruction nào, có sinh synthetic method hoặc `StringBuilder` hay không.
3. **Liên kết lúc chạy**: `invokedynamic` cùng bootstrap method liên kết call site.
4. **Triển khai tối ưu của JVM/JIT**: có thể tạo/ẩn class, inline, cache hoặc tạo mã máy theo cách riêng mà vẫn giữ đúng ngữ nghĩa được bảo đảm.

Mục tiêu: đọc được `javap -c -v -p`, phân tích `CONSTANT_InvokeDynamic_info` và `BootstrapMethods`, giải thích capture và functional interface, so sánh Java 8 với Java 9+/17 cho string concatenation, và không suy diễn một mẫu bytecode thành quy tắc bắt buộc cho mọi compiler.

## 1. Khung phiên bản: compiler, target class file và runtime là ba thứ khác nhau

| Phiên bản / lựa chọn | Lambda/method reference | Nối chuỗi `"a" + value` | Điều cần lưu ý |
| --- | --- | --- | --- |
| Java 7 trở về trước | Chưa có cú pháp lambda của Java 8 | Compiler thường dùng `StringBuilder` (hoặc một chiến lược tương thích khác) | JVM `invokedynamic` được bổ sung từ Java 7; có opcode không đồng nghĩa Java source đã có lambda |
| `javac` Java 8 | Thường dùng `invokedynamic` + `LambdaMetafactory` cho lambda/method reference | Thường dùng `new StringBuilder`, `append`, `toString` | Class file Java 8 có major version **52** |
| `javac` Java 9 trở lên với target hỗ trợ tính năng mới | Tiếp tục có thể dùng `invokedynamic` + `LambdaMetafactory` | Java 9 giới thiệu lowering bằng `invokedynamic` + `StringConcatFactory` (JEP 280) | Chiến lược, bootstrap arguments, bố trí synthetic methods có thể đổi theo compiler và bản build |
| Một `javac` hiện đại chạy `--release 8` | Có thể vẫn dùng `invokedynamic` cho lambda như Java 8 | `javac` thông thường sinh đường `StringBuilder` tương thích Java 8 | `--release 8` giới hạn ngôn ngữ/API/định dạng class file; **không biến compiler hiện đại thành đúng binary của javac 8** |
| `javac` hiện đại chạy `--release 17` | Có thể dùng `LambdaMetafactory` | Thường sinh `StringConcatFactory.makeConcatWithConstants` | Class file Java 17 có major version **61**, yêu cầu runtime đọc được phiên bản tương ứng |

**Chú ý:** đây là **các hành vi điển hình của OpenJDK `javac`**, không phải mệnh lệnh từ JLS rằng “mọi compiler phải tạo đúng opcode này”. Java source được chuẩn hóa về **ngữ nghĩa quan sát được**; JVM bytecode cũng có đặc tả riêng, nhưng compiler có nhiều lựa chọn biểu diễn hợp lệ.

Khi điều tra, ghi đủ bốn thông tin: `javac -version`; tùy chọn `--release` hoặc `-source`/`-target`; file `.class` thực sự được tạo ra; và `java -version` của JVM thực thi. Không lấy phiên bản runtime để suy ra chắc chắn phiên bản compiler đã tạo class.

## 2. Vì sao cần phân biệt các lệnh `invoke*`?

| Opcode | Ý nghĩa chính | Liên quan đến bài |
| --- | --- | --- |
| `invokestatic` | Gọi method static đã được liên kết; không có receiver instance | Compiler tạo helper static cho lambda hoặc gọi factory thường có thể dùng các method handle trỏ tới method static |
| `invokevirtual` | Gọi method instance class, có lựa chọn implementation theo runtime receiver | Lời gọi bên trong lambda có thể gọi method instance; chuỗi `StringBuilder.append` là invokevirtual |
| `invokeinterface` | Gọi method qua interface reference, chọn implementation phù hợp receiver | Gọi `IntUnaryOperator.applyAsInt` hoặc `Runnable.run` trên object hàm |
| `invokespecial` | Constructor, `super` và những lời gọi instance đặc biệt | `new StringBuilder; invokespecial <init>` trong lowering Java 8 |
| **`invokedynamic`** | Liên kết **call site** qua bootstrap method được mô tả trong class file | Điểm trung tâm của lambda/method reference và string concat hiện đại |

Không được đồng nhất **`invokedynamic` = “gọi phương thức ảo theo kiểu thực của đối tượng”**. `invokevirtual`/`invokeinterface` là một cơ chế khác. `invokedynamic` liên kết một `CallSite` có target `MethodHandle`; target đó có thể thực hiện nhiều loại thao tác do bootstrap quyết định.

Mỗi instruction `invokedynamic` trong `code[]` tham chiếu tới một entry loại `CONSTANT_InvokeDynamic_info` trong constant pool. Entry ấy dẫn tới một mục `BootstrapMethods` ở cấp class file và một `NameAndType` mô tả tên/descriptor của **dynamic call site**.

## 3. Đọc `CONSTANT_InvokeDynamic_info` và `BootstrapMethods`

Một cấu trúc khái niệm:

```text
code[] của method
  invokedynamic #N, 0
       |
       v
constant_pool[#N] = CONSTANT_InvokeDynamic_info
  ├── bootstrap_method_attr_index ───────┐
  └── name_and_type_index                │
         ├── tên call site               │
         └── descriptor call site        │
                                         v
                   BootstrapMethods[bootstrap_method_attr_index]
                     ├── bootstrap_method_ref
                     │     └── CONSTANT_MethodHandle_info
                     └── bootstrap_arguments[]
                           └── các constant pool entries
```

Trong tệp class, `CONSTANT_InvokeDynamic_info` có các trường `bootstrap_method_attr_index` và `name_and_type_index`. Chỉ số bootstrap trỏ tới **bảng BootstrapMethods**, khác với **index constant pool** được in ngay sau opcode. Số `#N` và số `#0` ở dòng `InvokeDynamic #0:...` **không phải** cùng một loại index.

Bootstrap method được JVM gọi trong quá trình liên kết call site, nhận các thông tin như `MethodHandles.Lookup`, tên call site, `MethodType` và các argument tĩnh. Nó trả về `CallSite` có target thích hợp. Sau khi liên kết, việc **thực thi dynamic call site** sử dụng target này với các **đối số động trên operand stack**.

Phân biệt ba mức dữ liệu:

- **Static bootstrap arguments**: được mô tả trong constant pool/`BootstrapMethods`; ví dụ method type và implementation method handle của lambda, hoặc concat recipe.
- **Dynamic call-site arguments**: giá trị thực trên operand stack khi instruction chạy; với capturing lambda, có thể là các giá trị được capture; với string concatenation, thường là các thành phần cần nối.
- **Đối số của lời gọi functional interface**: đến **sau** khi đã có object lambda, ví dụ `fn.applyAsInt(4)`; `4` không nhất thiết là argument của call site tạo lambda.

Đây là lý do hai instruction `invokedynamic` có thể trông giống nhau trong `javap` nhưng hoạt động rất khác nếu bootstrap methods khác.

## 4. Lambda expression: “tạo object hàm”, sau đó “gọi SAM” là hai bước

Một **functional interface** có một abstract method xét theo quy tắc Java; method này thường gọi là **SAM (single abstract method)**. Ví dụ `IntUnaryOperator` có `int applyAsInt(int operand)`.

Khi compiler nhìn thấy:

```java
int offset = 3;
IntUnaryOperator addOffset = x -> x + offset;
int answer = addOffset.applyAsInt(4);
```

Đây là **fragment minh họa**, giả sử đã import `java.util.function.IntUnaryOperator` và nằm trong method. Có thể tách thành:

1. Compiler kiểm tra lambda tương thích với target type `IntUnaryOperator`, xác định parameter/return type và biến capture.
2. Compiler tạo hoặc tham chiếu **implementation method** có body tương ứng (thường là method private static synthetic tên dạng `lambda$...`); đây là một **chiến lược của `javac`**, không phải tên bắt buộc theo JLS.
3. Bytecode đánh giá và đẩy biến capture `offset` lên operand stack rồi thực hiện **`invokedynamic`**; call site này tạo/trả về một object tương thích `IntUnaryOperator`.
4. Sau đó, `invokeinterface IntUnaryOperator.applyAsInt:(I)I` nhận object hàm và đối số `4` để chạy hành vi mà lambda cung cấp.

**Hai giai đoạn không nên gộp:** `invokedynamic` tại vị trí lambda thường **tạo/lấy function object**; `invokeinterface` tại vị trí `applyAsInt(4)` mới **gọi hàm**.

### Capturing và non-capturing

- `x -> x * 2` không cần đọc biến local bên ngoài: **non-capturing lambda**. Call site tạo function object thường có descriptor **không nhận capture argument**, ví dụ `()Ljava/util/function/IntUnaryOperator;`.
- `x -> x + offset` phải mang giá trị `offset` từ lexical scope: **capturing lambda**. Call site có thể có descriptor `(I)Ljava/util/function/IntUnaryOperator;`; `I` là kiểu capture `offset`.
- `IntUnaryOperator f = SomeClass::triple` có thể là **non-capturing method reference** trỏ tới method handle của `triple`, thường không cần compiler tạo helper `lambda$...` riêng.
- `instance::method` là **bound method reference**: receiver được đánh giá tại thời điểm tạo method reference và có thể là capture. Với receiver null, đánh giá bound reference có thể ném `NullPointerException` ngay lúc tạo.
- Với `ClassName::instanceMethod` dạng unbound method reference, receiver trở thành tham số khi gọi SAM, không phải capture sẵn một object cụ thể.

**Giá trị local được capture phải final hoặc effectively final.** Khi `offset` là primitive, lambda giữ giá trị đã capture, không “nhìn lại slot locals” của caller sau khi caller rời scope. Với một reference tới object mutable, lambda giữ reference; nó vẫn có thể nhìn thấy trạng thái **object** thay đổi sau đó. Không suy diễn capture theo Java là con trỏ tới một local slot đã hết vòng đời.

### Đừng dựa vào identity hay tên class của lambda object

Đặc tả không yêu cầu lambda được hiện thực bằng một anonymous class có tên cố định hoặc phải tạo một object mới ở mọi lần đánh giá non-capturing lambda. JVM có thể tái sử dụng instance, tạo hidden class hay tối ưu bằng JIT. Tránh kiểm thử `getClass().getName()`, class loader, tên synthetic methods, số lượng object hoặc `==` để suy ra “tính đúng” của lambda.

## 5. Vai trò của `LambdaMetafactory`

Trong OpenJDK `javac` điển hình, mục `BootstrapMethods` của lambda/method reference tham chiếu:

```text
REF_invokeStatic java/lang/invoke/LambdaMetafactory.metafactory
```

`LambdaMetafactory.metafactory` nhận thông tin về interface/SAM và method triển khai, rồi tạo call site có thể cho ra function object. Những tham số quan trọng về bản chất:

| Thông tin | Công dụng |
| --- | --- |
| `invokedType` (`MethodType` của call site) | Các kiểu capture đi vào dynamic call site và kiểu object functional interface trả về |
| `samMethodType` | Method type của SAM ở mức kiểu đã erasure/descriptor |
| `implMethod` (`MethodHandle`) | Implementation method được lambda object gọi, như synthetic helper hoặc method đích của method reference |
| `instantiatedMethodType` | Method type sau khi xét target functional interface/generics để kiểm tra và thích nghi kiểu |
| Tên SAM trên call site | Ví dụ `applyAsInt`, `run`, `apply` |

Không nhầm `samMethodType` với `instantiatedMethodType`: generic erasure có thể khiến hai kiểu khác nhau; implementation có thể cần cast, boxing/unboxing, widening hoặc bridge adaptation.

Một số trường hợp đặc biệt như lambdas **serializable**, marker interfaces hoặc bridge methods bổ sung có thể dùng `LambdaMetafactory.altMetafactory`. Không mặc định mọi `invokedynamic` trong một class đều dùng `metafactory`: nối chuỗi hiện đại thường có **bootstrap khác**.

## 6. Ví dụ Java 17 có thể chạy: lambda, capture và method reference

Tạo file **`LambdaTranslation.java`**:

```java
import java.util.function.IntUnaryOperator;

public class LambdaTranslation {
    private static IntUnaryOperator plus(int amount) {
        return x -> x + amount;
    }

    private static int triple(int value) {
        return value * 3;
    }

    public static void main(String[] args) {
        int offset = 3;

        IntUnaryOperator capturing = x -> x + offset;
        IntUnaryOperator plain = x -> x * 2;
        IntUnaryOperator methodReference = LambdaTranslation::triple;
        IntUnaryOperator fromFactory = plus(5);

        System.out.println("capture=" + capturing.applyAsInt(4));
        System.out.println("plain=" + plain.applyAsInt(4));
        System.out.println("methodRef=" + methodReference.applyAsInt(4));
        System.out.println("factory=" + fromFactory.applyAsInt(4));
    }
}
```

Chạy với **JDK 17+** có compiler hỗ trợ `--release 17`:

```bash
javac --release 17 LambdaTranslation.java
java LambdaTranslation
javap -c -v -p LambdaTranslation
```

**Stdout của riêng `java LambdaTranslation`:**

```text
capture=7
plain=8
methodRef=12
factory=9
```

Giải thích:

- `capturing` nhận `offset = 3` và argument `4` nên có kết quả `7`.
- `plain` không dùng capture và nhân `4 * 2 = 8`.
- `methodReference` trỏ tới static method `triple`, kết quả `4 * 3 = 12`.
- `fromFactory` capture tham số `amount = 5` trong method `plus`, nên `4 + 5 = 9`.

### Dấu vết thường gặp trong `javap`

Đọc method `main` sẽ thấy các call sites dạng **`invokedynamic`** để tạo các function objects, rồi các invocation dạng **`invokeinterface`** tới `IntUnaryOperator.applyAsInt:(I)I` khi thực hiện phép tính.

Ví dụ dạng **lược đồ**, không phải output nguyên văn với indexes cố định:

```text
iload_1
invokedynamic #A, 0    // InvokeDynamic: applyAsInt:(I)Ljava/util/function/IntUnaryOperator;
astore_2

invokedynamic #B, 0    // InvokeDynamic: applyAsInt:()Ljava/util/function/IntUnaryOperator;
astore_3

...                    // nạp lambda object và int 4
invokeinterface #C, 2 // IntUnaryOperator.applyAsInt:(I)I
```

Trong lược đồ, `iload_1` đại diện local chứa `offset`, còn `#A`/`#B`/`#C` là chỗ đứng cho constant pool indices thực tế. Vị trí locals/offset cụ thể phụ thuộc compiler; tập trung vào descriptor có **một capture argument** so với descriptor **không có capture**.

Dưới `BootstrapMethods`, có thể thấy `REF_invokeStatic LambdaMetafactory.metafactory`, `MethodType (I)I` và `REF_invokeStatic` trỏ tới implementation methods như `lambda$main$...` hoặc `triple`.

`javap -p` hiển thị các private/synthetic helpers; `javap -v` có thể hiển thị flags `ACC_SYNTHETIC`. Tên helper, số thứ tự lambda và thứ tự bootstrap entries **không ổn định như public API**.

### Vì sao ví dụ lambda cũng có `StringConcatFactory` khi compile `--release 17`?

Bốn câu `System.out.println("capture=" + ...)` còn chứa phép nối chuỗi với giá trị `int` động. Do đó **cùng một class** có thể chứa:

- `invokedynamic` dùng `LambdaMetafactory` để tạo lambda/method-reference object.
- `invokeinterface` để gọi SAM.
- `invokedynamic` dùng `StringConcatFactory` để nối nhãn với kết quả số.
- `invokevirtual` để gọi `PrintStream.println`.

Không đếm tất cả lệnh `invokedynamic` rồi kết luận có bấy nhiêu lambda. **Luôn lần theo từng bootstrap entry**.

## 7. Nối chuỗi trước Java 9: lowering thường dùng `StringBuilder`

Xét source:

```java
String result = "name=" + name + ", count=" + count;
```

Đây là **đoạn minh họa trong một method**. Trên `javac` Java 8 hoặc một `javac` hiện đại biên dịch với `--release 8`, bytecode điển hình có thể gồm:

```text
new java/lang/StringBuilder
dup
invokespecial java/lang/StringBuilder."<init>":()V
ldc "name="
invokevirtual java/lang/StringBuilder.append:(Ljava/lang/String;)Ljava/lang/StringBuilder;
aload_0
invokevirtual java/lang/StringBuilder.append:(Ljava/lang/String;)Ljava/lang/StringBuilder;
ldc ", count="
invokevirtual java/lang/StringBuilder.append:(Ljava/lang/String;)Ljava/lang/StringBuilder;
iload_1
invokevirtual java/lang/StringBuilder.append:(I)Ljava/lang/StringBuilder;
invokevirtual java/lang/StringBuilder.toString:()Ljava/lang/String;
areturn
```

Đây là **chuỗi opcode mô phỏng không có offset hoặc constant pool index**, không khẳng định `javac` phải sinh cùng một chuỗi hoặc thứ tự append theo mọi phiên bản. Ý tưởng: xây một mutable builder, nối các thành phần theo ngữ nghĩa Java rồi lấy String kết quả.

Hạn chế của cách biên dịch này là **chiến lược nối chuỗi bị “nướng” vào bytecode**: class file có các lời gọi `StringBuilder.append` cụ thể. JVM/JIT vẫn có thể tối ưu builder/escape analysis, nhưng compiler đã quyết định hình dạng bytecode ngay khi build.

JEP 280 thay đổi chiến lược này để runtime có thêm quyền chọn cách nối chuỗi mà không cần thay source; nó **không** thêm một toán tử nối chuỗi mới trong ngôn ngữ Java.

## 8. Từ Java 9: `StringConcatFactory` và concat call site bằng `invokedynamic`

Với `javac` Java 9+ target phù hợp, compiler thường nạp các thành phần động lên operand stack và thực thi **một `invokedynamic`** cho đoạn concat. Bootstrap có thể là:

- `StringConcatFactory.makeConcat`: nối các dynamic arguments theo method type.
- `StringConcatFactory.makeConcatWithConstants`: dùng thêm **recipe** và constants từ `BootstrapMethods`; đây là lựa chọn rất thường thấy trong class file do `javac` Java 17 sinh.

Dạng lược đồ cho ví dụ `"name=" + name + ", count=" + count`:

```text
aload_0
iload_1
invokedynamic #N, 0
  // InvokeDynamic: makeConcatWithConstants:
  // (Ljava/lang/String;I)Ljava/lang/String;
areturn
```

Nếu recipe là “`name=<arg>, count=<arg>`” theo nghĩa mô tả, runtime dùng nó để ghép hai dynamic arguments. Cách mã hóa chính xác trong API `StringConcatFactory`:

| Thành phần recipe | Ý nghĩa |
| --- | --- |
| Ký tự **U+0001** | Placeholder cho một **dynamic argument** được truyền vào call site |
| Ký tự **U+0002** | Placeholder cho một **static constant** truyền qua bootstrap arguments |
| Ký tự bình thường khác | Phần văn bản literal được chèn nguyên dạng trong kết quả |

Ví dụ recipe có thể được biểu diễn **bằng ký hiệu dễ đọc** là `name=[U+0001], count=[U+0001]`. Khi xem `javap -v` thực tế, các placeholder có thể được hiển thị như escape sequences chứ **không phải** chuỗi ký tự ngoặc vuông `[U+0001]`. Không suy ra mỗi dấu `+` trong source tương ứng một recipe entry hay một `invokedynamic` riêng.

**Khác biệt cốt lõi với lambda:**

- Lambda bootstrap trả về call site mà target **tạo function object**; sau đó SAM được gọi riêng.
- String-concat bootstrap trả về call site mà target **tính ra String kết quả trực tiếp từ các argument** của lần concat đó.
- Cùng opcode `invokedynamic` nhưng **bootstrap, call-site descriptor, target và sản phẩm đầu ra** khác nhau.

`StringConcatFactory` xuất hiện từ Java 9. Class Java 8 không thể phụ thuộc vào API này trên runtime Java 8; đó là lý do build cho `--release 8` cần lowering tương thích thay vì gọi `StringConcatFactory`.

## 9. Chương trình Java 17 để so sánh hai cách biên dịch

Tạo file độc lập **`StringConcatTranslation.java`**:

```java
public class StringConcatTranslation {
    static String message(String name, int count) {
        return "name=" + name + ", count=" + count;
    }

    static String sum(int a, int b) {
        return "sum=" + (a + b);
    }

    static String maybeNull(Object value) {
        return "item=" + value;
    }

    static String folded() {
        return "constant=" + "A" + 1 + 2;
    }

    public static void main(String[] args) {
        System.out.println(message("Lan", 7));
        System.out.println(sum(2, 3));
        System.out.println(maybeNull(null));
        System.out.println(folded());
    }
}
```

**Kết quả `java StringConcatTranslation`** giống nhau với class được biên dịch cho Java 8 hoặc Java 17, khi được chạy trên runtime phù hợp:

```text
name=Lan, count=7
sum=5
item=null
constant=A12
```

- `"name=" + name + ", count=" + count` thực hiện string conversion cho tham số động, nhưng compiler quyết định chuỗi bytecode để đạt kết quả.
- `"sum=" + (a + b)` tính số học `int` trước khi nối; nếu viết `"sum=" + a + b` sẽ nối tuần tự từ trái sang phải, cho `sum=23` với đầu vào 2 và 3.
- `"item=" + value` với `value == null` cho chuỗi ký tự `null`, không tự động ném `NullPointerException` chỉ vì nối reference null.
- `"constant=" + "A" + 1 + 2` là biểu thức hằng phù hợp; compiler có thể fold thành `"constant=A12"` và trả qua `ldc`, **không cần** `StringBuilder` hay `invokedynamic` cho chính method `folded`.

### Thực hành trên một JDK hiện đại có hỗ trợ hai release đích

Dùng **cùng một `javac`** (ví dụ JDK 17 hoặc 21), biên dịch tuần tự rồi lưu lại disassembly sau mỗi lần build:

```bash
javac -version

javac --release 8 StringConcatTranslation.java
javap -c -v -p StringConcatTranslation > concat-release8.txt
java StringConcatTranslation

javac --release 17 StringConcatTranslation.java
javap -c -v -p StringConcatTranslation > concat-release17.txt
java StringConcatTranslation
```

**Đọc output `javap` đã lưu:**

1. Với target 8, xem method `message`: class file major **52**; bản `javac` thông dụng thể hiện chuỗi `new StringBuilder`, `append` và `toString`.
2. Với target 17, class file major **61**; `message` thường chứa `invokedynamic makeConcatWithConstants` và có mục `BootstrapMethods` trỏ tới `StringConcatFactory`.
3. `sum` ở hai bản vẫn cần tính `a + b` thành một giá trị `int` trước khi concat; mã lệnh có thể xuất hiện `iadd` rồi đến builder hoặc indy.
4. `maybeNull` vẫn phải cho chuỗi `item=null`; chi tiết gọi `String.valueOf`/`StringBuilder.append` hoặc concat target là chuyện compiler/runtime.
5. `folded` có thể chỉ cần `ldc "constant=A12"` rồi `areturn` ở cả hai bản, vì constant folding.
6. Diff giữa hai output không chỉ do logic chương trình: còn có **constant pool indexes, `BootstrapMethods`, class-file version, line tables** và các quyết định riêng của compiler.

**Lưu ý về lệnh:** `--release` là tùy chọn của `javac` hiện đại, không có trong `javac` JDK 8. Khi dùng JDK 8 thật, compiler không nhận `--release 8`; bạn chỉ cần biên dịch như thông thường hoặc dùng flags mà chính compiler đó hỗ trợ. Một JDK mới có thể dần bỏ hỗ trợ target rất cũ, nên kiểm tra `javac --help` của bản đang dùng.

### So sánh “cùng compiler, khác --release” và “khác compiler”

Hai thực nghiệm trả lời hai câu hỏi **khác nhau**:

- Giữ `javac` JDK 17 cố định, đổi `--release 8` sang `--release 17`: đang khảo sát **quy tắc code generation theo target platform** mà compiler ấy hỗ trợ.
- Giữ `--release 17`, thay `javac` JDK 17 bằng `javac` phiên bản mới hơn: đang khảo sát **sự tiến hóa implementation/compiler** cho cùng target platform.

`--release 17` **không giả lập đúng byte-for-byte** output của `javac` JDK 17 khi được chạy bằng JDK khác; nó chủ yếu giới hạn ngôn ngữ, API nền tảng và class-file target. Nếu công cụ phân tích bytecode cần đúng hình dạng, hãy khóa và ghi cả **phiên bản/build của compiler**.

## 10. Phân tích sâu: phân biệt `BootstrapMethods` của lambda và nối chuỗi

Dùng:

```bash
javap -v -p LambdaTranslation
javap -v -p StringConcatTranslation
```

Tìm các mục ở constant pool:

```text
#N = InvokeDynamic    #bootstrapIndex:#nameAndTypeIndex
#X = MethodHandle     REF_invokeStatic ...
#Y = MethodType       ...
#Z = NameAndType      ...
```

Đây là **lược đồ**, indices thực tế khác nhau. Trong `BootstrapMethods` của `LambdaTranslation`:

- Một bootstrap thường trỏ `LambdaMetafactory.metafactory`; arguments gồm SAM method type, implementation method handle và instantiated method type.
- Capture parameter **không nhất thiết xuất hiện dưới dạng static bootstrap constant**; nó có thể là dynamic argument được load lên stack khi `invokedynamic` được thực thi.
- Với method reference `LambdaTranslation::triple`, implementation method handle thường trỏ thẳng tới `triple:(I)I`; với lambda có biểu thức `x + offset`, có thể trỏ tới synthetic helper method.
- Cùng file còn có thể có bootstrap `StringConcatFactory` cho các chuỗi in kết quả, cần phân biệt bằng bootstrap owner.

Trong `StringConcatTranslation` compiled target 17:

- Bootstrap của `message` thường là `StringConcatFactory.makeConcatWithConstants`, với recipe có placeholder cho `name` và `count`.
- Call-site type có thể có hai tham số động `(Ljava/lang/String;I)Ljava/lang/String;`.
- `folded` có thể không có dynamic call site nào vì được constant-fold.
- Không phải mỗi literal String trong recipe là một lời gọi `StringBuilder.append` hoặc một dynamic argument; compiler có thể nhúng literal vào recipe.

### Về lúc bootstrap được gọi

Resolution/linkage của `invokedynamic` có thể được VM thực hiện theo thời điểm phù hợp với đặc tả, thường khi một call site được sử dụng lần đầu. Việc **liên kết call site** không đồng nghĩa bootstrap method phải chạy **ở mỗi lần thực thi** instruction. Sau liên kết, target đã được xác lập cho call site theo quy tắc phù hợp.

Nếu bootstrap method thất bại, lỗi liên kết như `BootstrapMethodError` có thể xuất hiện. Đây khác với một exception mà **body lambda** ném sau khi người dùng gọi SAM, hoặc một exception do `toString()` trên object động phát sinh trong quá trình nối chuỗi.

## 11. Method reference và target type quyết định adapter

Ngoài lambda thông thường, method reference cũng là một **poly expression**: kiểu của nó phụ thuộc **functional interface target** tại vị trí sử dụng.

Ví dụ:

```java
import java.util.function.Function;
import java.util.function.Supplier;

Function<String, Integer> length = String::length;
Supplier<String> greeting = " hello "::trim;
```

Đây là **fragment minh họa**, không phải chương trình độc lập. Với `String::length` ở đây, receiver là **đối số** của `Function.apply`. Với `" hello "::trim`, receiver cụ thể được **capture khi method reference được đánh giá**, và `Supplier.get()` sau đó dùng receiver ấy.

Khi một method reference có kiểu SAM như `Function<String,Integer>`, quá trình liên kết có thể cần boxing/unboxing hoặc method-type adaptation giữa `String.length:()I` và interface `Function.apply:(Ljava/lang/Object;)Ljava/lang/Object;`. `javap` có thể chỉ ra bridge/adapter qua metadata của metafactory; không được suy ra từ generic type ở source rằng bytecode có đúng một method descriptor giống nguyên văn source.

Một mẹo đọc: xác định **SAM descriptor đã erasure**, **implementation MethodHandle**, và **instantiated method type**. Ba thông tin ấy giải thích cách object lambda cung cấp được một method phù hợp giao diện.

## 12. Lambda object không phải anonymous inner class viết bằng source

Hai đoạn Java có thể có cùng mục đích:

```java
Runnable lambda = () -> System.out.println("run");

Runnable anonymous = new Runnable() {
    @Override
    public void run() {
        System.out.println("run");
    }
};
```

Đây là **fragment minh họa**. Khi dùng OpenJDK `javac` thông thường:

- Lambda thường được hạ thành một `invokedynamic` liên kết với `LambdaMetafactory`; compiler có thể tạo một helper method cho body.
- Anonymous class là một **class declaration** thực sự trong Java source, thường tạo thêm class file như `Outer$1.class`, với constructor và method `run()`.
- Trong lambda, từ khóa `this` theo lexical scope chỉ **đối tượng bao quanh** (nếu có); trong anonymous class, `this` chỉ **instance anonymous class**. Đây là khác biệt **ngữ nghĩa**, không thể quyết định chỉ từ việc cả hai cùng implement `Runnable`.
- Một non-capturing lambda có thể được tái sử dụng object khi được đánh giá nhiều lần; không nên áp đặt số lần cấp phát từ một benchmark hoặc heap dump lên JLS contract.
- Compiler được phép chọn biểu diễn khác cho lambda miễn duy trì ngữ nghĩa; vì vậy không xây công cụ trên giả định luôn có file class với tên dạng `$$Lambda$...`.

Cách so sánh bytecode đúng là quan sát **method chứa biểu thức**, **BootstrapMethods** và **các class files phát sinh** trong chính build đang phân tích, chứ không đồng nhất mọi function object với anonymous class.

## 13. Điều gì thuộc bảo đảm của JLS, điều gì là chi tiết compiler/runtime?

| Phát biểu | Phân loại | Cách dựa vào nó |
| --- | --- | --- |
| Lambda phải tương thích functional-interface target type và quy tắc effectively final | **JLS contract** | Có thể dùng làm quy tắc kiểm tra đúng/sai source |
| `applyAsInt(4)` phải cung cấp kết quả đúng của implementation lambda | **Hợp đồng ngôn ngữ/API** | Có thể viết test kết quả và lỗi |
| `javac` sinh method tên `lambda$main$0` | **Chi tiết code generation** | Chỉ dựa khi kiểm thử một compiler/build đã khóa |
| Mọi lần đánh giá lambda đều tạo object mới | **Không được bảo đảm nói chung** | Không phụ thuộc identity object |
| `invokedynamic` có quy tắc constant pool và bootstrap method | **JVMS contract** | Dùng kiểm tra tính hợp lệ class file |
| Java 8 `javac` dùng chuỗi `StringBuilder.append` | **Mẫu compiler theo phiên bản** | Dùng so sánh class files của compiler ấy |
| Java 9+ `javac` dùng `StringConcatFactory` theo JEP 280 | **Lựa chọn code generation phổ biến** | Xác minh bằng `javap` trên build thực |
| Một constant expression có thể được compiler gộp sẵn | **Quy tắc Java về constant expressions + lựa chọn sinh code** | Không đếm opcode theo số toán tử `+` |
| Bootstrap call site được cache/triển khai bằng class nào | **Chi tiết runtime** | Không lấy làm API ổn định |
| String concat với `null` cho literal `null` | **Ngữ nghĩa Java string conversion** | Có thể viết test theo giá trị kết quả |

Một class file hợp lệ có thể chạy cùng một kết quả trên nhiều JVM nhưng có hiệu năng, số object hoặc machine-code khác nhau. Trái lại, hai file class khác nhau hoàn toàn về `StringBuilder`/`invokedynamic` vẫn có thể cùng tuân theo ngữ nghĩa source Java.

## 14. Lỗi thực tế khi nâng JDK và công cụ phân tích bytecode

**1. Instrumentation tìm `StringBuilder.append` rồi bị “mất dấu” nối chuỗi.** Khi chuyển compiler target 8 → 17, concat có thể thành `invokedynamic`. Công cụ bytecode analysis phải hỗ trợ cả hai mẫu hoặc theo dõi ý nghĩa động qua bootstrap, thay vì giả định số lệnh `append` không đổi.

**2. Công cụ chỉ nhìn opcode `invokedynamic` mà không đọc bootstrap.** Nó có thể nhầm lambda factory với string concat; phải xem `CONSTANT_InvokeDynamic_info`, `NameAndType`, `BootstrapMethods` và owner như `LambdaMetafactory` hay `StringConcatFactory`.

**3. Compiler mới sinh class-file version không được runtime cũ hỗ trợ.** Ví dụ Java 17 class-file major 61 không chạy trực tiếp trên JVM Java 8; lỗi `UnsupportedClassVersionError` là vấn đề phiên bản runtime/classfile, không phải “lambda lỗi”.

**4. Cố chạy class tham chiếu `StringConcatFactory` trên Java 8.** API này chỉ có từ Java 9; một class file hoặc tool tự tạo tham chiếu nó không thể kỳ vọng liên kết trên runtime Java 8. Dùng đúng target và chạy test trên runtime hỗ trợ.

**5. Dùng `-source 8 -target 8` rồi nghĩ đã giới hạn API.** Khi build với `javac` mới, hai flags này không thay thế hoàn toàn `--release 8` về kiểm tra API nền tảng. Với môi trường hỗ trợ, `--release` phù hợp hơn cho cross-compilation.

**6. Tin rằng Java 17 source bắt buộc luôn chứa StringConcatFactory trong class file.** Nếu biểu thức concat là constant expression, compiler có thể fold; nếu code viết rõ `new StringBuilder()`, compiler không bị buộc thay source đó thành concat indy.

**7. Thấy method `lambda$...` và gọi trực tiếp vào nó từ thư viện khác.** Nó là chi tiết compiler (thường private/synthetic), không phải public API ổn định; tên/ký hiệu có thể đổi khi biên dịch lại.

**8. Bắt lỗi từ code chạy và nhầm với `BootstrapMethodError`.** Nếu lambda body ném `IllegalArgumentException` sau khi gọi SAM, đó không mặc định là lỗi bootstrap; phân biệt **call-site linkage** với **target execution**.

**9. Suy số lượng lambda objects từ số lệnh indy.** Một call site có thể được thực thi nhiều lần và runtime có quyền tái sử dụng instances trong trường hợp phù hợp; allocation/lifetime không suy trực tiếp từ instruction count.

**10. Đọc `javap` mà bỏ qua compiler flags.** Compiler options, debug info, optimization, vendor và target release có thể thay đổi bytecode offsets, synthetic members, constant-pool indices hoặc bootstrap entries. Lưu thông tin toolchain cùng artifact phân tích.

## 15. Quy trình kiểm tra một class file có lambda và string concat

1. **Ghi môi trường:** `javac -version`, `java -version`, `--release`, bản build, `gradle/maven` toolchain và JAR đã thực thi.
2. **Xác nhận class major:** `javap -v ClassName`; ví dụ Java 8 → 52, Java 17 → 61.
3. **Tìm call site:** `javap -c -v -p ClassName`; lọc từng `invokedynamic` theo **method chứa instruction**.
4. **Tra constant pool:** ở index `#N`, kiểm tra loại `InvokeDynamic` và NameAndType descriptor.
5. **Lần sang `BootstrapMethods`:** ghi owner/method của bootstrap, các method types, implementation handles, recipes, constants.
6. **Với lambda:** xem capture arguments trước `invokedynamic`; xem body helper hoặc method reference target; tìm `invokeinterface` ở nơi SAM được gọi.
7. **Với string concat:** tìm thành phần dynamic đưa lên stack, recipe U+0001/U+0002 hoặc builder chain, kiểm tra constant folding và thứ tự phép tính.
8. **So sánh hai builds:** đảm bảo dùng đúng file class sau mỗi lần compile, không nhìn nhầm cached build output; diff disassembly theo ý nghĩa chứ không chỉ theo `#index`.
9. **Chạy test hành vi:** kết quả, ngoại lệ và side effects theo JLS/API quan trọng hơn việc bytecode trông giống kỳ vọng.
10. **Đối chiếu đặc tả đúng phiên bản:** chỉ nâng hành vi từ “quan sát compiler” thành quy tắc chung nếu thực sự được JLS/JVMS/API bảo đảm.

### Checklist câu hỏi tự kiểm tra

- `invokedynamic` ở chỗ lambda tạo **function object** hay thực hiện một phép **string concatenation**?
- Method reference có capture receiver không? Lambda có capture biến effectively final không?
- `LambdaMetafactory` nhận SAM type, implementation handle và instantiated type nào?
- `StringConcatFactory` nhận call-site argument types, recipe và static constants nào?
- Biểu thức nối chuỗi có constant-fold được không? Dấu ngoặc có đổi phép cộng số học thành nối chuỗi sớm không?
- Mục tiêu chạy Java 8 hay Java 17? `--release` có giới hạn API/classfile phù hợp không?
- Đoạn bytecode mình trích là JVMS bắt buộc hay một lựa chọn của compiler được dùng tại thời điểm build?
- Công cụ bytecode/agent có hiểu `BootstrapMethods`, `invokedynamic` và class-file major version không?

## Tài liệu tham chiếu

- [JLS Java SE 17 `15.27` — Lambda Expressions](https://docs.oracle.com/javase/specs/jls/se17/html/jls-15.html#jls-15.27): target typing, lambda semantics, capture.
- [JLS Java SE 17 `15.13` — Method Reference Expressions](https://docs.oracle.com/javase/specs/jls/se17/html/jls-15.html#jls-15.13): bound/unbound method references và runtime evaluation.
- [JLS Java SE 17 `15.18.1` — String Concatenation Operator](https://docs.oracle.com/javase/specs/jls/se17/html/jls-15.html#jls-15.18.1): string conversion và ngữ nghĩa toán tử `+`.
- [JVMS Java SE 17 `4.4.10` — `CONSTANT_Dynamic_info` và `CONSTANT_InvokeDynamic_info`](https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-4.html#jvms-4.4.10): cấu trúc constant pool cho dynamic call sites.
- [JVMS Java SE 17 `4.7.23` — `BootstrapMethods` attribute](https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-4.html#jvms-4.7.23): bootstrap method handles và static arguments.
- [JVMS Java SE 17 `5.4.3.6` — Dynamically-Computed Call Site and Constant Resolution](https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-5.html#jvms-5.4.3.6): linkage bootstrap.
- [JVMS Java SE 17 `6.5` — `invokedynamic` instruction](https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-6.html#jvms-6.5.invokedynamic): operand stack và instruction semantics.
- [`LambdaMetafactory` — Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/invoke/LambdaMetafactory.html): metafactory và altMetafactory cho lambda/method reference.
- [`StringConcatFactory` — Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/invoke/StringConcatFactory.html): makeConcat, makeConcatWithConstants và recipe U+0001/U+0002.
- [JEP 280 — Indify String Concatenation](https://openjdk.org/jeps/280): lý do chuyển chiến lược sinh bytecode sang invokedynamic từ Java 9.
- [`javac` và `javap` — Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/specs/man/javac.html) / [`javap`](https://docs.oracle.com/en/java/javase/17/docs/specs/man/javap.html): `--release` và cách quan sát class file.

**Ghi nhớ:** Lambda trong `javac` hiện đại thường tạo **function object qua `invokedynamic + LambdaMetafactory`**, sau đó caller gọi SAM; String `+` trên target Java 9+ thường tính **kết quả String qua `invokedynamic + StringConcatFactory`**, thay cho builder chain điển hình ở target Java 8. **Bootstrap method và descriptor mới cho biết call site làm gì; compiler/version mới cho biết tại sao một source lại ra hình dạng bytecode ấy.**


</details>

- [Quay lại đầu trang](#back-to-top)
