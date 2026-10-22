<a id="back-to-top"></a>

# Lệnh bytecode

## Menu
- [Máy dựa trên ngăn xếp: opcode, toán hạng và bộ đếm lệnh](#bytecode-instructions-001)
- [Nạp hằng số và chuyển dữ liệu giữa biến cục bộ với operand stack](#bytecode-instructions-002)
- [Nhóm lệnh số học, bit, so sánh và chuyển kiểu](#bytecode-instructions-003)
- [Nhóm lệnh pop, dup, swap và ràng buộc kích thước giá trị](#bytecode-instructions-004)
- [Nhánh điều kiện, goto, vòng lặp và địa chỉ đích](#bytecode-instructions-005)
- [tableswitch và lookupswitch trong biểu diễn switch](#bytecode-instructions-006)
- [new, newarray, anewarray và multianewarray khi tạo dữ liệu](#bytecode-instructions-007)
- [getfield, putfield, getstatic và putstatic khi truy cập trường](#bytecode-instructions-008)
- [checkcast, instanceof, arraylength và kiểm tra khi truy cập mảng](#bytecode-instructions-009)
- [Đọc bytecode biểu thức, vòng lặp và phép gán để dự đoán thứ tự thực thi](#bytecode-instructions-010)

## <a id="bytecode-instructions-001">Máy dựa trên ngăn xếp: opcode, toán hạng và bộ đếm lệnh</a>

<details>
<summary>Xem chi tiết</summary>

# Máy dựa trên ngăn xếp: opcode, toán hạng và bộ đếm lệnh

## 1. Từ một dòng Java tới các bước thực thi trong JVM

Khi đọc Java, chúng ta thường thấy một biểu thức như:

```java
int result = (a + b) * 2;
```

Trong chương trình được biên dịch thông thường, JVM thực thi một chuỗi **lệnh bytecode** mô tả các bước nhỏ: lấy giá trị từ biến, đưa lên **operand stack**, cộng hai số, đẩy hằng số 2, nhân, rồi lưu kết quả về biến. JVM vì thế được gọi là **máy ảo dựa trên ngăn xếp** (*stack-based virtual machine*).

Một ví dụ bytecode mô hình hóa biểu thức trên:

```text
iload_0      ; đưa giá trị biến cục bộ a lên operand stack
iload_1      ; đưa giá trị biến cục bộ b lên operand stack
iadd         ; lấy hai int trên đỉnh stack, cộng, đẩy kết quả
iconst_2     ; đẩy hằng số int 2
imul         ; lấy hai int trên đỉnh stack, nhân, đẩy kết quả
istore_2     ; lấy kết quả khỏi stack, ghi vào biến cục bộ result
```

Ba ý niệm xuyên suốt bài:

- **Opcode** (*operation code*) cho biết JVM phải làm thao tác gì: `iadd`, `iload_0`, `goto`, `invokevirtual`…
- **Toán hạng** (*operand*) là dữ liệu mà thao tác sử dụng: có thể là các giá trị đã có trên **operand stack**, hoặc các byte **đi kèm opcode** như chỉ số biến cục bộ, số hằng, chỉ số constant pool, độ lệch nhảy.
- **Program counter (PC)** giúp luồng thực thi xác định **instruction bytecode hiện tại**. Nhảy nhánh thay đổi nơi thực thi tiếp theo; lời gọi phương thức tạo frame mới cho phương thức được gọi.

Đây là mô hình máy ảo ở cấp **bytecode**, không có nghĩa CPU thật của máy tính phải chạy bằng ngăn xếp. Một JVM như HotSpot có thể thông dịch bytecode hoặc biên dịch JIT thành mã máy sử dụng thanh ghi CPU. Ngữ nghĩa cần giữ đúng là ngữ nghĩa do đặc tả JVM quy định.

## 2. Các bộ phận phải phân biệt: frame, locals, operand stack và PC

Một JVM thread có **JVM stack** riêng; mỗi lời gọi phương thức tạo một **frame** mới. Frame chứa thông tin liên quan đến một lần gọi, trong đó có **mảng biến cục bộ** và **operand stack**. Theo JVMS, mỗi thread cũng có **PC register** riêng.

```text
Thread đang chạy
├── PC register: địa chỉ instruction JVM đang thực thi
└── JVM stack (ngăn xếp các lời gọi phương thức)
    ├── Frame của method hiện tại (trên cùng)
    │   ├── Local variables: slot 0, 1, 2, ...
    │   ├── Operand stack: [..., phần tử đỉnh]
    │   └── Tham chiếu tới runtime constant pool của lớp
    ├── Frame của method gọi nó
    └── Frame cũ hơn
```

### 2.1. JVM stack khác operand stack

Hai khái niệm có cùng chữ “stack” nhưng khác trách nhiệm:

| Thành phần | Chứa gì? | Thời điểm thay đổi |
| --- | --- | --- |
| **JVM stack** của một thread | Các **frame** của những lời gọi phương thức đang hoạt động | Gọi phương thức → tạo/đẩy frame; return → frame tương ứng kết thúc |
| **Operand stack** bên trong một frame | Các giá trị trung gian mà các instruction đang thao tác | `iload` đẩy giá trị, `iadd` lấy toán hạng, `istore` lấy kết quả |
| **Local variables** bên trong frame | Các slot chứa tham số và giá trị cục bộ của phương thức | `iload` đọc, `istore` ghi, `iinc` cập nhật slot |
| **PC register** của thread | Vị trí instruction JVM đang được thực thi trong ngữ cảnh hiện tại | Tiến tới instruction kế tiếp, nhảy nhánh, thực thi lời gọi… |

Đừng đồng nhất **operand stack** với cấu trúc dữ liệu `java.util.Stack` hoặc với **call stack** hiển thị trong stack trace. `java.util.Stack` là một lớp thư viện; hai cấu trúc thực thi là những phần của mô hình JVM.

### 2.2. Local variables là các slot có chỉ số

Phương thức `static int sum(int a, int b)` thường đặt `a` ở local slot 0 và `b` ở slot 1. Một phương thức instance thường có `this` ở slot 0; các tham số bắt đầu từ các slot tiếp theo. Các giá trị `long` và `double` chiếm **hai local variable slots**, trong khi phần lớn kiểu khác chiếm một slot.

Ví dụ khái niệm:

```text
static int sum(int a, int b)
locals:
  [0] a = 3
  [1] b = 4
  [2] result (được ghi sau)

int Person.compute(int n)
locals:
  [0] this   (reference)
  [1] n      (int)
```

Chỉ số slot không phải tên biến nguồn. Compiler có thể sử dụng lại slot tại các phạm vi khác nhau. Khi `javap -c` hiển thị `iload_0`, con số `0` là **local index**, không phải giá trị của biến.

### 2.3. Operand stack là vùng làm việc tạm thời

Các instruction lấy/đẩy giá trị theo quy tắc **LIFO (last in, first out)**. Dùng ký hiệu sau để mô phỏng:

```text
[]            = stack rỗng
[3]           = giá trị 3 ở đỉnh
[3, 4]        = 4 ở đỉnh, 3 ngay dưới
[7]           = kết quả sau khi iadd lấy 3 và 4
```

Mỗi phương thức có `max_stack` trong `Code` attribute để giới hạn độ sâu operand stack cần thiết; `max_locals` giới hạn số slot local. Đây là **giới hạn của phương thức**, không phải số lượng luôn tồn tại ở mọi thời điểm.

Một `int` hoặc tham chiếu thường chiếm một đơn vị stack; `long` và `double` thuộc **category 2** và chiếm hai đơn vị. Các instruction `dup`, `pop`, `swap` có quy tắc chặt về category nên không thể tùy tiện đảo những giá trị hai đơn vị như giá trị một đơn vị.

## 3. Một instruction JVM được mã hóa như thế nào?

Mô hình lệnh:

```text
+-----------+-------------------------------+
| opcode    | operand bytes nếu lệnh cần    |
| 1 byte    | 0, 1, 2 hoặc nhiều byte       |
+-----------+-------------------------------+
```

Opcode được mã hóa bằng **một byte** và cho biết loại thao tác. Nhiều instruction có các **operand bytes** đi ngay sau opcode; độ dài instruction không cố định. Những byte này có thể biểu diễn số tức thời, chỉ số local, chỉ số vào constant pool hoặc độ lệch nhảy.

Ví dụ:

| Mnemonic | Ý nghĩa | Operand mã hóa sau opcode? |
| --- | --- | --- |
| `iconst_2` | Đẩy hằng int 2 | Không |
| `iload_0` | Đọc local slot 0 kiểu int và đẩy lên stack | Không: chỉ số 0 nằm trong tên opcode |
| `iload 5` | Đọc local slot 5 kiểu int | Có: chỉ số local |
| `bipush 100` | Đẩy số nguyên 100 từ immediate dạng signed byte | Có: một byte |
| `sipush 1000` | Đẩy immediate dạng signed short | Có: hai byte |
| `ldc #k` | Nạp hằng phù hợp từ runtime constant pool | Có: index tới constant pool |
| `iinc 2, 1` | Tăng local int slot 2 thêm 1 | Có: local index và signed increment |
| `if_icmpge target` | So sánh hai int rồi nhảy khi điều kiện thỏa | Có: nhánh tương đối (signed offset) |
| `invokestatic #k` | Gọi phương thức static qua symbolic reference | Có: index vào constant pool |

**Hai nghĩa của “toán hạng” phải được tách rõ:**

1. **Toán hạng của phép tính ở runtime:** chẳng hạn `iadd` lấy **hai giá trị int đã có trên operand stack**. Bytecode của `iadd` không chứa trực tiếp hai số đó.
2. **Toán hạng được mã hóa trong instruction:** chẳng hạn `bipush 100` có byte chứa 100, `iload 5` chứa local index, `goto` chứa branch displacement.

Do vậy câu “`iadd` không có operands” chỉ đúng khi nói về **operand bytes sau opcode**. Về **ngữ nghĩa tính toán**, `iadd` vẫn cần hai toán hạng trên stack.

### 3.1. Mnemonic không phải bytecode thô

`iload_0`, `iadd` và `invokevirtual` là **tên dễ đọc** của opcode trong bản disassembly. Công cụ `javap` biến opcode bytes thành mnemonic và in thêm operand dưới dạng số, tên lớp/phương thức hoặc comment đã giải quyết từ constant pool.

Với `javap -c`:

```text
  0: iload_0
  1: iload_1
  2: iadd
```

Các số **0, 1, 2** ở đầu dòng là **bytecode offsets**, không phải opcode và cũng không phải số dòng Java. Việc offset đi từ 0 → 1 → 2 trong ví dụ là do cả ba instruction đều dài một byte.

### 3.2. Các cách nạp hằng số khác nhau

- `iconst_m1`, `iconst_0` … `iconst_5`: nạp các hằng int rất nhỏ, không cần operand bytes.
- `bipush`, `sipush`: immediate signed 8-bit / 16-bit, kết quả được đẩy như `int`.
- `ldc`: nạp một hằng được hỗ trợ từ constant pool, ví dụ chuỗi hoặc số.
- `ldc_w`: dạng index constant pool lớn hơn `ldc`.
- `ldc2_w`: dùng với hằng category-2 như `long` và `double` theo quy tắc instruction.

Compiler chọn dạng thích hợp; không nên suy luận một câu lệnh `int n = 100;` bắt buộc phải sinh `bipush` trên mọi compiler hay mọi cách tối ưu.

### 3.3. Không phải instruction nào cũng có độ dài cố định

Các instruction như `tableswitch` và `lookupswitch` có **padding** để căn chỉnh dữ liệu nhảy và mang nhiều cặp/offset. `wide` sửa cách giải mã operand của một số instruction liên quan local variables hoặc `iinc` để hỗ trợ index/counter lớn. `goto_w` có branch offset rộng hơn `goto`.

Hệ quả: **PC không tăng một số cố định sau mọi opcode**. Nếu một instruction dài 3 byte và luồng đi tuần tự, offset kế tiếp thường cách 3 byte; nếu có nhánh, PC hướng tới một vị trí khác hoàn toàn.

## 4. Các nhóm opcode cốt lõi của một máy dựa trên ngăn xếp

| Nhóm | Opcode tiêu biểu | Điều thay đổi |
| --- | --- | --- |
| Nạp hằng | `iconst_0`, `bipush`, `ldc`, `aconst_null` | Đẩy giá trị lên operand stack |
| Nạp local | `iload`, `aload`, `lload`, `iload_0` | Đọc local slot → đẩy lên stack |
| Lưu local | `istore`, `astore`, `lstore`, `istore_1` | Lấy giá trị từ stack → ghi local slot |
| Số học | `iadd`, `isub`, `imul`, `idiv`, `ladd` | Lấy toán hạng, tính, đẩy kết quả |
| Chuyển kiểu | `i2l`, `l2i`, `d2i` | Lấy giá trị, chuyển theo quy tắc JVM, đẩy kết quả |
| Quản lý stack | `pop`, `dup`, `swap`, `dup2` | Sắp xếp/nhân bản/hủy giá trị stack theo category |
| So sánh/nhánh | `ifeq`, `if_icmpgt`, `ifnull`, `goto` | Đọc điều kiện từ stack, có thể đổi vị trí thực thi |
| Lời gọi/return | `invokestatic`, `invokevirtual`, `invokeinterface`, `ireturn`, `return` | Truyền tham số, tạo/hoàn tất lời gọi phương thức |
| Trường | `getstatic`, `putstatic`, `getfield`, `putfield` | Đọc/ghi field, tiêu thụ/đẩy reference và giá trị |
| Đối tượng/mảng | `new`, `newarray`, `anewarray`, `iaload`, `iastore`, `arraylength` | Tạo đối tượng, truy cập mảng, dùng reference |
| Kiểm tra/ngoại lệ | `checkcast`, `instanceof`, `athrow` | Kiểm tra kiểu reference hoặc ném ngoại lệ |
| Đồng bộ | `monitorenter`, `monitorexit` | Thao tác monitor theo quy tắc JVM |

Các tiền tố thường gặp thể hiện loại giá trị theo JVM: `i` cho `int`, `l` cho `long`, `f` cho `float`, `d` cho `double`, `a` cho **reference** trong các instruction như `aload`/`astore`. Nhưng **không phải mọi instruction có tiền tố `a` đều cùng mẫu**; hãy đọc định nghĩa opcode cụ thể.

Đừng hiểu bảng như danh sách **toàn bộ** bytecode JVM. Mục đích là nhận ra cách các instruction thao tác trên locals, operand stack và control flow; các lệnh chuyên biệt có thêm điều kiện về kiểu, cấu trúc và ngoại lệ.

## 5. Mô phỏng chính xác `iload`, `iadd`, `imul` và `istore`

Giả sử phương thức static có hai tham số:

```java
static int combine(int a, int b) {
    int result = (a + b) * 2;
    return result;
}
```

Với bytecode điển hình từ `javac` trên Java 17:

```text
0: iload_0
1: iload_1
2: iadd
3: iconst_2
4: imul
5: istore_2
6: iload_2
7: ireturn
```

Giả sử gọi `combine(3, 4)`. Trước instruction đầu tiên, locals chứa `a = 3` và `b = 4`, operand stack rỗng.

| PC offset | Instruction | Stack trước → sau (đỉnh bên phải) | Locals liên quan |
| ---: | --- | --- | --- |
| 0 | `iload_0` | `[] → [3]` | `slot0=3` |
| 1 | `iload_1` | `[3] → [3,4]` | `slot1=4` |
| 2 | `iadd` | `[3,4] → [7]` | Không đổi |
| 3 | `iconst_2` | `[7] → [7,2]` | Không đổi |
| 4 | `imul` | `[7,2] → [14]` | Không đổi |
| 5 | `istore_2` | `[14] → []` | `slot2=14` |
| 6 | `iload_2` | `[] → [14]` | `slot2=14` |
| 7 | `ireturn` | `[14] → []` trong callee | Trả `14` cho caller |

Tại **offset 2**, `iadd` lấy hai số theo thứ tự trên operand stack và đẩy tổng. Tại **offset 4**, `imul` lấy `7` và `2` rồi đẩy `14`. Tại **offset 5**, `istore_2` làm operand stack rỗng bằng cách lưu `14` vào local slot 2.

Với lệnh `ireturn`, giá trị được chuyển thành kết quả của lời gọi phương thức: caller nhận `14` tại vị trí tương ứng trên **operand stack của caller**, còn frame callee kết thúc. Không có một operand stack dùng chung cho tất cả phương thức.

### 5.1. Vì sao `max_stack = 2` ở phương thức này?

Trong bảng, độ sâu lớn nhất của operand stack là **2 đơn vị**: `[3, 4]` hoặc `[7, 2]`. `max_locals` cần **3 slots** để chứa `a`, `b` và `result`. `javap -v` thường hiển thị cho `combine` metadata tương ứng:

```text
stack=2, locals=3, args_size=2
```

Dòng trên thuộc kiểu output của `javap` cho phiên bản `javac` thông thường; nếu xem output thực tế, nên đối chiếu cả đoạn code thay vì dựa vào một bản in từ môi trường khác.

**Không tính `max_stack` bằng số biến của chương trình.** Nó là mức stack tối đa dùng khi thực thi bytecode trong frame, không phải số lượng biến khai báo hoặc số frame của call stack.

### 5.2. Thử biểu thức có toán tử không giao hoán

`isub` và `idiv` cần chú ý thứ tự giá trị. Nếu stack là `[a, b]` (b ở đỉnh), thì `isub` tính `a - b` và `idiv` tính `a / b` theo quy tắc chia số nguyên JVM.

Ví dụ:

```text
iconst_5     → [5]
iconst_2     → [5, 2]
isub         → [3]       // 5 - 2, không phải 2 - 5
```

Không mô tả stack như “lấy đỉnh làm toán hạng trái” một cách tổng quát; thứ tự operand của từng instruction được đặc tả rõ.

## 6. PC register và bytecode offset: điều gì đang được đếm?

**Program Counter (PC) register** là trạng thái riêng của **mỗi JVM thread**. Trong mô hình của JVMS, khi thread đang thực thi phương thức **không phải native**, PC chứa địa chỉ của instruction JVM đang được thực thi. Nếu phương thức hiện tại là **native**, giá trị PC register **không được đặc tả**.

Điều này không phải lời cam kết rằng PC là một biến Java đọc trực tiếp được, cũng không có nghĩa ứng dụng nên suy đoán số PC từ stack trace. PC là một thành phần của **mô hình thực thi JVM**, không phải API truy xuất thông thường như `Thread.currentThread()`.

### 6.1. Đọc ba cột trong output `javap`

```text
  0: iload_0
  1: iload_1
  2: iadd
  3: iconst_2
  4: imul
  5: istore_2
  6: iload_2
  7: ireturn
```

- **Bên trái dấu `:`:** bytecode offset, tính bằng byte kể từ đầu `code[]` của phương thức.
- **Bên phải:** opcode mnemonic (và operand nếu có).
- **PC:** về mặt logic chỉ tới instruction JVM đang thực thi của thread, nên có thể liên hệ với những offset này khi phân tích bytecode.

Không nhầm **bytecode offset** với **chỉ số instruction**. Nếu instruction tại offset 1 dài 3 byte, instruction kế tiếp có thể bắt đầu tại offset 4. Các offset không nhất thiết tăng đều một đơn vị.

### 6.2. Chạy tuần tự, nhảy và lời gọi phương thức

Trong một đường chạy không có nhánh, JVM tiến tới instruction kế tiếp. Nhưng có ba tình huống cơ bản khác:

1. **Rẽ nhánh:** `if…` hoặc `goto` chuyển điều khiển tới target instruction; PC tương ứng với đích đã chọn.
2. **Gọi phương thức:** `invoke…` thiết lập lời gọi mới, một frame của phương thức được gọi được kích hoạt; khi nó return, caller tiếp tục tại điểm sau lời gọi theo ngữ nghĩa thực thi.
3. **Ngoại lệ:** khi lỗi/`athrow` xảy ra, điều khiển có thể chuyển tới exception handler hợp lệ; nếu không có handler trong frame thì frame có thể kết thúc bất thường và việc tìm handler tiếp tục ở caller.

PC không “cộng đều cho đến khi chạm cuối tệp class”. Mỗi phương thức có `code[]` riêng, và JVM có quy tắc cụ thể để giải mã instruction, giải quyết branch target và chuyển control flow.

### 6.3. JVM đa luồng có một PC hay nhiều PC?

Mỗi JVM thread có **PC register riêng**, vì các thread có thể đang thực thi những instruction ở các phương thức khác nhau hoặc các vị trí khác nhau của cùng một phương thức. JVM stack và operand stack trong các frame cũng gắn với thread tương ứng.

```text
Thread A                             Thread B
PC → methodA, offset 12             PC → methodB, offset 5
JVM stack A                         JVM stack B
  frame methodA                       frame methodB
    locals / operand stack              locals / operand stack
```

Hai thread có thể chia sẻ heap và đối tượng, nhưng **không chia sẻ PC register hay frame đang thực thi**. Bộ lập lịch có thể tạm dừng/chạy lại một thread; hành vi scheduling của hệ điều hành và triển khai runtime không thay đổi quy tắc PC trong mô hình JVM.

## 7. Branch operands: chuyển PC bằng độ lệch tương đối

Nhiều lệnh nhánh như `ifeq`, `if_icmpge` hoặc `goto` dùng **branch offset tương đối**, được tính từ **địa chỉ của opcode đang nhảy**, không phải từ địa chỉ instruction kế tiếp.

Ví dụ sơ đồ có offset giả định:

```text
 0: iconst_0
 1: istore_1
 2: iload_0
 3: ifle       12
 6: iconst_1
 7: istore_1
 8: goto       12
11: nop
12: iload_1
13: ireturn
```

**Đây là sơ đồ minh họa, không khẳng định `javac` thực tế phải sinh đúng chuỗi trên.** Ví dụ nhánh tại offset 3 sang offset 12 có độ lệch logic **12 - 3 = +9** byte. Nếu một `goto` ở offset 8 chuyển tới 12, độ lệch tương ứng là **+4** byte.

`javap` thường in luôn **target offset tuyệt đối** như `ifle 12`, giúp người đọc không cần tự giải mã giá trị signed branch displacement nằm trong class file. Trong bytecode thô, operand branch của `ifle` là một số có dấu **16 bit** (hai byte); `goto_w` dùng offset có dấu 32 bit.

Tại một nhánh có điều kiện, verifier và runtime cần bảo đảm target là điểm hợp lệ của instruction, không rơi vào giữa phần operand bytes của instruction khác. Luồng thực thi phải tuân thủ các quy tắc về kiểu và operand stack tại điểm hội tụ.

### 7.1. `if…` tiêu thụ giá trị trên stack

Ví dụ biểu thức `if (n == 0)` có thể biên dịch thành:

```text
iload_0          ; stack [] → [n]
ifeq target      ; lấy n; nếu n == 0 thì nhảy
```

Sau khi `ifeq` kiểm tra, giá trị `n` đã được **lấy khỏi operand stack** trên cả hai đường; nó không tự nằm lại đó. Một số opcode dùng so sánh **một** giá trị với 0/null (`ifeq`, `ifnull`), trong khi `if_icmp…` so sánh **hai** giá trị int từ stack.

### 7.2. Vòng lặp cần nhảy ngược

Một vòng `for` có thể tạo nhánh về offset thấp hơn để kiểm tra điều kiện lặp. Trong trường hợp này, PC đi đến một instruction **trước đó** chứ không tăng mãi.

```text
[Khởi tạo] → [Kiểm tra điều kiện] → sai → [Thoát vòng]
                  |
                đúng
                  v
              [Thân vòng]
                  |
              [Cập nhật]
                  |
                  +--------→ [Kiểm tra điều kiện]
```

Tùy compiler, `for` có thể sinh `goto` về đầu vòng, hoặc dùng một instruction nhánh có điều kiện để quay lại. Cấu trúc chính vẫn là **control flow graph** có cạnh quay ngược; không phải lệnh “lặp” đặc biệt theo nghĩa mọi vòng đều dùng đúng một opcode.

### 7.3. `tableswitch` và `lookupswitch`

Hai instruction này dùng cho những dạng `switch` thích hợp:

- `tableswitch` mô tả nhiều target qua dải giá trị nguyên tương đối liên tục.
- `lookupswitch` biểu diễn những cặp giá trị khóa/target; thích hợp với những trường hợp khóa thưa theo chiến lược compiler.

Cả hai có padding và branch operands nhiều byte; độ dài phụ thuộc số trường hợp. Không khẳng định mọi `switch` trong Java dùng `tableswitch`; compiler lựa chọn dựa trên ngữ nghĩa, mật độ khóa và chiến lược sinh mã.

## 8. Lời gọi phương thức: truyền argument qua stack và chuyển frame

Xét:

```java
static int twice(int x) {
    return x * 2;
}

static int caller() {
    return twice(4);
}
```

Trong caller, compiler có thể đặt giá trị 4 lên operand stack rồi dùng `invokestatic` để gọi `twice`. Caller cung cấp arguments theo phương thức được mô tả trong **method descriptor**. Khi gọi:

```text
Caller frame:
  ... operand stack [4]
             |
             | invokestatic twice:(I)I
             v
Callee frame twice:
  local slot 0 = 4
  operand stack = []
  ... thực thi iload_0, iconst_2, imul, ireturn ...
             |
             | kết quả 8
             v
Caller frame:
  ... operand stack [8]
```

Những điểm then chốt:

- `invokestatic` lấy các tham số phù hợp từ operand stack của caller.
- Phương thức được gọi chạy với **frame mới**, có locals và operand stack riêng.
- Nếu callee return giá trị `int` bằng `ireturn`, caller nhận giá trị đó vào operand stack của mình.
- Nếu phương thức có kiểu trả về `void`, instruction `return` không đưa giá trị trả về cho caller.
- Với `invokevirtual` hoặc `invokeinterface`, thông thường còn có **receiver reference** cùng arguments trên operand stack; việc liên kết và dispatch phải tuân theo các quy tắc JVM.

Khi gặp `invokevirtual`, tên opcode chưa đủ để biết phương thức cụ thể được dispatch tới implementation nào. Runtime có thể xác định đích theo lớp thực tế của đối tượng và quy tắc gọi phương thức. Một receiver null có thể dẫn tới `NullPointerException`; đó là lỗi runtime hợp lệ, không đồng nghĩa class file có opcode sai.

### 8.1. Descriptor gắn operand stack với chữ ký phương thức

Ví dụ descriptor:

```text
(II)I                    // hai tham số int, trả int
(Ljava/lang/String;)V    // một String reference, trả void
([I)J                   // một int[] reference, trả long
```

`I` biểu diễn `int`, `J` biểu diễn `long`, `V` dùng cho `void` ở kiểu trả về, `L...;` cho class/interface, và `[` cho array.

Verifier dùng các quy tắc này để biết instruction gọi phương thức phải tiêu thụ bao nhiêu giá trị và thuộc loại gì, cũng như giá trị trả về nào sẽ xuất hiện trên stack của caller. Phải tính đúng **category-2 slots** với `long`/`double`.

### 8.2. `invoke…` liên quan constant pool

Đa số lệnh gọi phương thức dùng một **chỉ số tới runtime constant pool** của lớp hiện tại, nơi chứa symbolic reference hay thông tin lời gọi phù hợp. Khi liên kết, JVM giải quyết các tham chiếu theo những quy tắc riêng, có thể dẫn đến các lỗi linkage nếu class/phương thức không hợp lệ hoặc không truy cập được.

Không diễn giải operand của `invokevirtual #k` là “địa chỉ hàm native cố định”. Nó là một tham chiếu theo định dạng class file để JVM có thể liên kết đúng ngữ nghĩa.

## 9. Reference, object và array trong mô hình stack

Máy dựa trên stack không chỉ làm số học. Với object và array, **giá trị tham chiếu** cũng được lưu trong locals và operand stack.

Ví dụ:

```java
String word = "Java";
int size = word.length();
```

Một chuỗi opcode thường liên quan:

```text
ldc #k                   ; tham chiếu String constant
astore_0                 ; lưu reference vào local slot 0
aload_0                  ; đẩy reference lên operand stack
invokevirtual #method    ; gọi String.length() trên receiver
istore_1                 ; lưu int kết quả
```

Dấu `#k` và `#method` biểu thị **constant-pool index** mang tính minh họa. Compiler có thể bố trí local slot khác theo cách biên dịch.

Đối với mảng:

```java
int[] values = new int[2];
values[0] = 9;
int first = values[0];
```

Một phép ghi phần tử `iastore` cần trên operand stack theo thứ tự thích hợp: **array reference**, **index**, **giá trị int**. Phép đọc `iaload` cần **array reference** và **index**, rồi đẩy giá trị đã đọc. Runtime còn kiểm tra null reference, chỉ số mảng và các ràng buộc liên quan; một số thao tác có thể ném `NullPointerException` hoặc `ArrayIndexOutOfBoundsException`.

`aconst_null` đẩy một null reference lên operand stack. Nó là một giá trị có ý nghĩa trong mô hình kiểu reference; opcode không tạo ra một “đối tượng null”. Không được dùng null để gọi instance method mà không gặp kiểm tra runtime tương ứng.

### `new` và trạng thái chưa khởi tạo

Để tạo object, mã bytecode điển hình có chuỗi dạng:

```text
new MyClass
dup
invokespecial MyClass.<init>:()V
```

Lệnh `new` cấp phát đối tượng và đẩy một reference **chưa khởi tạo hoàn chỉnh** theo quy tắc verifier; `dup` thường nhân bản giá trị trên stack để giữ một reference cho phần sử dụng sau constructor; `invokespecial` gọi constructor tương ứng. Việc dùng reference chưa khởi tạo bị ràng buộc chặt; không thể sửa bytecode để bỏ qua các bước khởi tạo hợp lệ rồi mong verifier chấp nhận.

## 10. Ví dụ Java 17+ hoàn chỉnh: xem operand stack và PC bằng `javap`

Tạo tệp **`StackMachineDemo.java`**:

```java
public final class StackMachineDemo {
    static int combine(int a, int b) {
        int result = (a + b) * 2;
        return result;
    }

    static int sumTo(int n) {
        int total = 0;
        for (int i = 1; i <= n; i++) {
            total += i;
        }
        return total;
    }

    static String describe(int value) {
        return value >= 0 ? "nonnegative" : "negative";
    }

    public static void main(String[] args) {
        System.out.println("combine(3, 4) = " + combine(3, 4));
        System.out.println("sumTo(4) = " + sumTo(4));
        System.out.println("describe(-1) = " + describe(-1));
    }
}
```

Từ thư mục chứa tệp, chạy bằng **JDK 17 trở lên**:

```bash
javac --release 17 StackMachineDemo.java
java StackMachineDemo
```

**Stdout chính xác**:

```text
combine(3, 4) = 14
sumTo(4) = 10
describe(-1) = negative
```

Ba phương thức biểu diễn những hành vi khác nhau:

1. **`combine`:** nạp local, đẩy stack, tính số học, lưu local và return.
2. **`sumTo`:** dùng local `total` và `i`, phép tăng, so sánh, nhảy điều kiện và quay lại vòng lặp; PC không đi thẳng từ offset 0 tới cuối một lần duy nhất.
3. **`describe`:** kiểm tra điều kiện và chọn một trong hai constant String; nhánh quyết định instruction kế tiếp.

Lệnh xem disassembly:

```bash
javap -c -p StackMachineDemo
```

Cờ `-c` in code của phương thức, `-p` hiện cả thành viên không public. Để xem cấu trúc rộng hơn, dùng:

```bash
javap -v -p StackMachineDemo
```

`-v` giúp thấy constant pool, method descriptors, `Code` attribute, `stack`/`locals`, các bảng frame khi có, và các thông tin class file khác.

### 10.1. Kết quả disassembly để đối chiếu với `combine`

Với `javac` của JDK 17 và đúng mã `combine` ở trên, phần method về mặt opcode là:

```text
static int combine(int, int);
  Code:
     0: iload_0
     1: iload_1
     2: iadd
     3: iconst_2
     4: imul
     5: istore_2
     6: iload_2
     7: ireturn
```

Phần offset là **độ lệch byte trong `code[]`**; đây không phải thứ tự dòng nguồn. Theo bảng mô phỏng ở mục 5:

```text
PC   instruction     operand stack (sau instruction)
0    iload_0          [3]
1    iload_1          [3,4]
2    iadd             [7]
3    iconst_2         [7,2]
4    imul             [14]
5    istore_2         []
6    iload_2          [14]
7    ireturn          kết thúc frame và trả 14
```

Kiểm tra bản của bạn khi dùng compiler khác: JVM chỉ yêu cầu code hợp lệ và đúng ngữ nghĩa, không bắt mọi compiler sinh **cùng chuỗi instruction** cho một mã nguồn. Việc tối ưu hóa JIT sau đó cũng không thay đổi văn bản bytecode mà `javap` đọc từ tệp `.class`.

### 10.2. Vì sao `sumTo` chứng minh PC có thể quay lại?

`sumTo(4)` thực hiện:

```text
total = 0
i = 1 → total = 1
i = 2 → total = 3
i = 3 → total = 6
i = 4 → total = 10
i = 5 → điều kiện i <= n sai → return 10
```

Khi disassemble, tìm opcode nhóm `if_icmp…`/`if…`, `goto` và `iinc` (chi tiết phụ thuộc compiler). Một số target nhảy sẽ trở lại bytecode offset đã đi qua để tiếp tục vòng; cuối cùng nhánh điều kiện chuyển PC tới vùng return. Đây là cách một **control-flow graph** biểu diễn vòng lặp bằng các instruction cơ bản.

### 10.3. Vì sao `describe` không bắt buộc có “opcode ternary”?

Toán tử `?:` thuộc cú pháp Java. Compiler có thể chuyển nó thành nhánh điều kiện, nạp một hằng String từ constant pool rồi `areturn`. Trong bytecode, không có yêu cầu phải tồn tại một opcode chung tên `ternary`. Mô hình JVM thực thi các bước **so sánh → nhảy → nạp reference → return**.

### 10.4. Tự kiểm tra constant pool

Ở `javap -v`, các operand có dạng `#k` thường tham chiếu tới constant pool. Với lời gọi trong `main`, bạn sẽ thấy các constant-pool entries hoặc thông tin liên quan đến in ra stdout và string concatenation. Trên Java 17, trình biên dịch có thể dùng **`invokedynamic`** cho nối chuỗi; không có gì mâu thuẫn với mô hình stack: arguments vẫn được chuẩn bị và tiêu thụ theo descriptor của lời gọi.

`invokedynamic` là một opcode có quy tắc liên kết riêng; không cần giải thích toàn bộ bootstrap method ở bài này để hiểu rằng nó **đọc operands qua runtime constant pool và tiêu thụ giá trị từ operand stack**.

## 11. Branch offset và kích thước instruction: bài mô phỏng nhỏ

Giả sử có các bytecode offsets sau trong một ví dụ **giả định**:

```text
0:  iconst_1
1:  istore_0
2:  iload_0
3:  ifeq          11
6:  bipush        10
8:  istore_0
9:  goto          13
12: nop
13: iload_0
14: ireturn
```

Sơ đồ này chỉ để hình dung kích thước instruction và đường nhảy; nếu dùng như class file thật, mọi target (kể cả offset 11 được minh họa phía trên) **phải trỏ tới opcode boundary hợp lệ**. Trong chuỗi minh họa này offset 11 rơi vào operand bytes của `goto`, nên **đây là ví dụ về một branch target sai và sẽ không là bytecode hợp lệ**.

Hãy sửa target của `ifeq` thành offset **12**:

```text
3: ifeq 12  // displacement = 12 - 3 = +9
```

Rồi để branch target và stack states đều hợp lệ, cần bảo đảm mọi đường đi hội tụ không vi phạm quy tắc stack/locals. Ví dụ sửa một displacement chỉ khắc phục **vị trí target**, không tự đảm bảo toàn bộ frame/StackMapTable đã đúng.

Cách học an toàn là:

1. Trước hết xác định **bytecode instruction boundary** theo độ dài từng opcode.
2. Tính branch displacement từ **offset opcode nhảy** đến **offset opcode đích**.
3. Kiểm tra **stack height và verification types** tại target trên mọi đường đi.
4. Nếu lớp thuộc định dạng yêu cầu stack map frames, đối chiếu/cập nhật `StackMapTable` phù hợp.

Kỹ thuật này đặc biệt quan trọng khi đọc bytecode do công cụ instrument/transform tạo ra.

## 12. Kiểu dữ liệu ảnh hưởng đến thao tác stack ra sao?

Không thể thay một opcode bằng opcode khác chỉ vì cả hai “đều cộng hai số”:

| Lệnh | Operand trên stack | Kết quả |
| --- | --- | --- |
| `iadd` | Hai `int` | `int` |
| `ladd` | Hai `long` | `long` |
| `fadd` | Hai `float` | `float` |
| `dadd` | Hai `double` | `double` |
| `i2l` | Một `int` | `long` |
| `l2i` | Một `long` | `int` |
| `aload` | Một local có kiểu reference phù hợp | Đẩy reference |
| `astore` | Một reference trên stack | Ghi reference vào local |

Vì category-2 values chiếm hai đơn vị stack, `long`/`double` cần được phân biệt khi tính `max_stack` và xử lý các instruction như `dup2`. Một kiểu như `byte` hay `boolean` thường được biểu diễn và thao tác dưới dạng kiểu `int` trong nhiều instruction JVM, nhưng điều đó không xóa các quy tắc kiểm tra/chuyển đổi riêng ở biên như array stores hoặc descriptors.

### Ví dụ category-2

```java
static long add(long a, long b) {
    return a + b;
}
```

Một dạng bytecode hợp lệ:

```text
lload_0
lload_2
ladd
lreturn
```

Vì mỗi tham số `long` dùng **hai local slots**, `a` bắt đầu tại slot 0 và `b` tại slot 2. Không viết `lload_1` cho tham số `b` trong bố cục này. Khi cả hai `long` nằm trên operand stack, chúng chiếm tổng cộng **bốn đơn vị** stack; phép `ladd` tạo một `long` chiếm hai đơn vị.

Với phương thức instance, slot 0 còn dành cho `this`, nên các chỉ số có thể dịch đi. Hãy xác định chữ ký và modifier của phương thức trước khi suy ra bố cục locals.

## 13. Bộ xác minh bytecode kiểm tra những gì?

JVM verifier kiểm tra các ràng buộc **cấu trúc và kiểu** để không có một luồng instruction hợp lệ nào dùng stack/locals theo cách vi phạm yêu cầu của opcode. Các kiểm tra liên quan:

- `iadd` nhận hai giá trị kiểu verifier phù hợp, không nhận reference như `String`.
- `aload` đọc local slot có giá trị reference hợp lệ; `iload` yêu cầu kiểu số nguyên thích hợp.
- Mọi branch target hợp lệ và các đường đi đến target có trạng thái kiểu tương thích.
- Operand stack không vượt quá `max_stack` theo quy tắc của phương thức.
- Return opcode phù hợp return type trong method descriptor, ví dụ `ireturn` khi trả `int`.
- Receiver/arguments của lời gọi phù hợp quy tắc method invocation.
- Các reference chưa khởi tạo trong constructor được quản lý theo quy tắc verifier.

`StackMapTable` trong class file giúp khai báo **verification type state** tại các offset quan trọng để verifier đối chiếu với luồng bytecode. Nó không phải stack đang chứa các giá trị thực tế tại một thời điểm chạy cụ thể. Phần metadata này hỗ trợ xác minh kiểu, không phải “bộ đếm lệnh”.

**Verifier không chứng minh chương trình không có lỗi nghiệp vụ.** Một phương thức `idiv` được kiểm tra kiểu thành công vẫn có thể ném `ArithmeticException` khi mẫu số bằng 0. Tương tự, reference null hợp lệ về kiểu vẫn có thể ném `NullPointerException` khi dereference.

Nếu bytecode bị biến đổi sai sau biên dịch, JVM có thể từ chối class bằng `VerifyError` hoặc lỗi định dạng/liên kết khác tùy bản chất sai. Hãy điều tra opcode, stack states, offsets, class version và `StackMapTable` trước khi kết luận.

## 14. PC, exception và `athrow`

Các bước thực thi không phải lúc nào cũng tiếp tục từ instruction ngay sau instruction hiện tại. Xét:

```java
static int safeDivide(int a, int b) {
    try {
        return a / b;
    } catch (ArithmeticException ex) {
        return -1;
    }
}
```

Với `b = 0`, phép chia số nguyên tạo `ArithmeticException`. JVM tìm một handler thích hợp theo **exception table** của `Code` attribute. Nếu handler được tìm thấy trong phương thức, điều khiển chuyển tới bytecode offset của handler và operand stack ở handler nhận exception reference theo quy tắc xử lý ngoại lệ.

Khung mô hình:

```text
... iload_0, iload_1, idiv ...
               |
           b == 0
               |
               v
       ArithmeticException
               |
               v
  exception table → handler PC
               |
               v
  handler nhận reference ngoại lệ
               |
               v
  trả về -1
```

Đừng hiểu rằng PC luôn chuyển sang instruction kế tiếp sau `idiv`. Khi exception được ném và có handler, control flow **nhảy tới handler**. Nếu không có handler phù hợp trong frame, việc tìm kiếm tiếp tục qua stack frames của caller; stack trace ghi nhận call frames liên quan chứ không phải một bản log toàn bộ PC offsets đã đi qua.

`athrow` là instruction ném một object reference kiểu ngoại lệ phù hợp. Nó thay đổi control flow tương tự: không chỉ là một thao tác “pop rồi next PC” thông thường.

## 15. JVM dựa trên stack có luôn chậm hơn máy dựa trên thanh ghi không?

**Không thể kết luận chỉ từ mô hình bytecode.** Stack-based ISA của JVM dùng nhiều lệnh ngắn như `iload_0`, `iadd`, `istore_1` và biểu diễn toán hạng ngầm định qua operand stack. Điều đó có thể giúp định dạng bytecode gọn và tạo ra một hợp đồng thực thi tương đối độc lập CPU.

Tuy nhiên, JVM có thể:

- **Thông dịch:** đọc opcode và thực hiện các thao tác tương ứng.
- **Biên dịch JIT:** chuyển vùng bytecode thường chạy thành mã máy phù hợp CPU, tối ưu qua các biểu diễn trung gian, phân bổ thanh ghi, inline, v.v.
- **Tối ưu hóa runtime:** thay đổi chiến lược tùy profile và môi trường triển khai.

Vì vậy **operand stack là mô hình ngữ nghĩa bytecode**; không có yêu cầu mỗi `iadd` luôn biến thành một cặp push/pop thật trên stack phần cứng. Một compiler JIT có thể giữ các giá trị trung gian ở thanh ghi CPU trong mã máy cuối.

Mọi nhận định về hiệu năng cần benchmark workload cụ thể, có warm-up phù hợp, xem cả thời gian khởi động và cách đo. Đếm số instruction bytecode **không đủ** để dự đoán tốc độ thực thi.

## 16. Những sai lầm thường gặp khi đọc `javap`

| Nhầm lẫn | Cách hiểu đúng |
| --- | --- |
| “`0:` là số dòng Java” | Đây là bytecode offset trong `code[]` của method |
| “Mỗi opcode dài một byte nên instruction luôn dài một byte” | Opcode một byte; operands có thể kéo dài instruction, switch có độ dài biến thiên |
| “`iadd` không có toán hạng” | Không có operand bytes, nhưng **lấy hai giá trị** từ operand stack |
| “`iload_0` nạp hằng 0” | Nó **đọc local slot 0**; `iconst_0` mới đẩy hằng số 0 |
| “`istore_2` lưu vào heap object thứ hai” | Nó ghi **local slot 2** của frame phương thức |
| “Operand stack là call stack” | Operand stack thuộc **mỗi frame**; call stack chứa nhiều frame |
| “`max_stack` là số lần gọi hàm tối đa” | Là độ sâu operand stack tối đa của một method theo đơn vị stack |
| “PC là biến toàn cục của JVM” | Mỗi JVM thread có PC riêng |
| “PC luôn tăng 1” | Instruction có độ dài khác nhau; nhánh, method call, exception đổi control flow |
| “`goto 15` là offset delta +15” | `javap` hiển thị target offset; class file mã hóa branch displacement tương đối |
| “`long` chiếm một local slot như `int`” | `long`/`double` dùng **hai local slots** |
| “Mã JIT thực thi operand stack bằng RAM stack thật ở từng opcode” | JIT có thể ánh xạ ngữ nghĩa bytecode sang thanh ghi/mã máy |
| “Verifier chứng minh mọi input chạy thành công” | Nó kiểm tra an toàn cấu trúc/kiểu, không chứng minh không có exception hợp lệ |

Một lỗi khác là lấy một đoạn `javap` từ phương thức khác rồi suy ngược vị trí local không có chữ ký method. Ví dụ `iload_1` trong instance method có thể là tham số đầu tiên, còn trong static method có thể là tham số thứ hai. Phải xét **có `this` ở slot 0 hay không**, và lưu ý kiểu category-2.

## 17. Tự luyện đọc bytecode

### Bài 1. Đọc operand stack

Cho đoạn bytecode giả định hợp lệ:

```text
0: iconst_3
1: iconst_4
2: iadd
3: iconst_2
4: imul
5: ireturn
```

Hãy viết stack sau từng instruction và kết quả return.

**Đáp án:**

```text
0 → [3]
1 → [3, 4]
2 → [7]
3 → [7, 2]
4 → [14]
5 → return 14
```

### Bài 2. Phân biệt immediate và runtime operand

Cho `bipush 100` và `iadd`. Lệnh nào có operand byte đi kèm? Lệnh nào lấy giá trị từ operand stack?

**Đáp án:** `bipush` có operand byte biểu diễn 100 và đẩy 100. `iadd` không có operand bytes nhưng tiêu thụ hai giá trị `int` từ operand stack rồi đẩy tổng.

### Bài 3. PC và branch displacement

Một opcode `goto` nằm tại offset 20, đích nằm tại offset 7. Độ lệch signed được mã hóa theo khái niệm là bao nhiêu?

**Đáp án:** `7 - 20 = -13`, tính từ offset opcode `goto`, **không** từ offset ngay sau `goto`.

### Bài 4. Local slots

Một static method nhận `(long first, double second, int third)`. Những tham số bắt đầu ở slot nào?

**Đáp án:** `first` ở slot 0–1; `second` ở slot 2–3; `third` ở slot 4. Nếu là instance method thì còn receiver `this` ở slot 0 và các tham số dịch tương ứng.

### Bài 5. `iload_0` và `iconst_0` khác nhau ra sao?

**Đáp án:** `iload_0` lấy giá trị `int` từ local slot 0; `iconst_0` đẩy giá trị hằng số `0`, không cần đọc local.

### Bài 6. Vì sao một method với `max_stack=2` vẫn gọi được method khác?

**Đáp án:** `max_stack` giới hạn operand stack **trong frame hiện tại**. Lời gọi tạo/kích hoạt frame khác với locals và operand stack của callee; không dùng giới hạn 2 đó làm số frame tối đa cho JVM stack.

### Bài 7. Một đoạn code Java chạy êm với dữ liệu đầu vào A. Điều đó đủ kết luận bytecode luôn hợp lệ không?

**Đáp án:** Không. Verifier phải kiểm tra các ràng buộc kiểu/luồng ở cấp class file; một thử nghiệm cụ thể không khám phá mọi nhánh. Ngược lại, qua verification cũng **không** bảo đảm không có lỗi runtime nghiệp vụ.

### Bài 8. Tại sao không thể suy ra CPU đang dùng những thanh ghi nào từ `javap -c`?

**Đáp án:** `javap` hiển thị **bytecode JVM**, không phải mã máy sau khi JIT tối ưu. Cách JVM ánh xạ operand stack sang thanh ghi CPU là chi tiết của runtime và phiên bản thực thi.

## 18. Tóm tắt và tài liệu chính thức

Chuỗi tư duy đúng khi đọc bytecode:

```text
instruction offset / PC
          |
          v
Đọc opcode và operand bytes nếu có
          |
          v
Kiểm tra kiểu và trạng thái operand stack / locals trước lệnh
          |
          v
Áp dụng hiệu ứng pop, push, load, store hoặc branch
          |
          v
Xác định instruction tiếp theo (tuần tự, nhảy, gọi/return, exception)
          |
          v
Lặp lại cho đến khi phương thức kết thúc
```

**Opcode** quy định thao tác, **operand bytes** mang những tham số mã hóa của một số instruction, **operand stack** mang nhiều toán hạng runtime, **local variables** chứa tham số/giá trị cục bộ, và **PC register** xác định vị trí instruction hiện tại của mỗi thread. Bốn thành phần này liên kết để tạo mô hình thực thi stack-based của JVM.

Đọc thêm:

- [JVM Specification 2.5 — Run-Time Data Areas](https://docs.oracle.com/javase/specs/jvms/se21/html/jvms-2.html#jvms-2.5): PC register, JVM stacks và những vùng dữ liệu runtime.
- [JVM Specification 2.6 — Frames](https://docs.oracle.com/javase/specs/jvms/se21/html/jvms-2.html#jvms-2.6): local variables, operand stacks, frame và cách truyền giá trị khi gọi/return.
- [JVM Specification 4.7.3 — Code Attribute](https://docs.oracle.com/javase/specs/jvms/se21/html/jvms-4.html#jvms-4.7.3): `max_stack`, `max_locals`, mảng bytecode `code[]` và exception table.
- [JVM Specification 6 — The JVM Instruction Set](https://docs.oracle.com/javase/specs/jvms/se21/html/jvms-6.html): semantics, operands, cấu trúc và ngoại lệ của từng opcode.
- [`javap` Tool Documentation (JDK 21)](https://docs.oracle.com/en/java/javase/21/docs/specs/man/javap.html): `-c`, `-p` và `-v` để đọc code/attribute.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="bytecode-instructions-002">Nạp hằng số và chuyển dữ liệu giữa biến cục bộ với operand stack</a>

<details>
<summary>Xem chi tiết</summary>

# Nạp hằng số và chuyển dữ liệu giữa biến cục bộ với operand stack trong JVM

Trong Java Virtual Machine (JVM), nhiều biểu thức Java được thực hiện bằng một chuỗi thao tác rất cụ thể: **đẩy hằng số lên operand stack**, **nạp giá trị từ local variables lên stack**, thực hiện phép tính hoặc gọi phương thức, rồi **lưu kết quả từ stack về local variables**. Nếu hiểu các lệnh này, bạn có thể đọc kết quả `javap -c`, theo dõi từng giá trị trong một JVM frame và phân biệt được thao tác ở mức bytecode với câu lệnh Java.

Bài học tập trung vào nhóm bytecode **constant loading** (`iconst_*`, `bipush`, `sipush`, `ldc`, `ldc_w`, `ldc2_w`...), **local load/store** (`iload`/`istore`, `lload`/`lstore`...), chỉ số slot, giá trị 1 hoặc 2 slot, lệnh `wide` và `iinc`. Mốc tài liệu là **JVMS Java SE 17**. Mã ví dụ sử dụng Java 17+, và những opcode do `javac` chọn sẽ được phân biệt với quy tắc bắt buộc của đặc tả.

## 1. Khung thực thi: locals và operand stack thuộc về *mỗi lần gọi phương thức*

Một lần gọi phương thức JVM tạo một **frame** chứa hai vùng quan trọng:

- **Local variable array**: các ô (slot) đánh chỉ số từ `0`, chứa tham số, receiver `this` (đối với instance method) và các giá trị cục bộ/tạm. Mảng này truy cập theo **index**.
- **Operand stack**: ngăn xếp LIFO chứa toán hạng và kết quả trung gian. Phần lớn lệnh số học lấy toán hạng từ đỉnh stack và đẩy kết quả trở lại.
- **`max_locals` và `max_stack`** trong `Code` attribute của phương thức xác định số slot locals và độ sâu tối đa của operand stack. Đây là giới hạn **theo frame**, không phải dung lượng heap hay toàn bộ Java thread stack.

Sơ đồ luồng dữ liệu thường gặp:

```text
Local variables                     Operand stack (đáy → đỉnh)
slot 0: a = 4                          []
slot 1: b = 5                          []
      | iload_0                          [4]
      | iload_1                          [4, 5]
      | iadd                             [9]
      | istore_2                         []
slot 2: sum = 9   <---------------------+
```

`iload_0` không lấy giá trị “ra khỏi” local: nó **sao chép giá trị** từ slot 0 lên stack. Ngược lại, `istore_2` **lấy giá trị từ đỉnh stack** để gán vào slot 2, vì vậy stack giảm độ sâu. Local slot 0 vẫn là 4, slot 1 vẫn là 5 sau phép cộng.

Một method có nhiều lần gọi đồng thời (nhiều thread hoặc đệ quy) sẽ có **các frame riêng**. Không được hiểu local slot 0 là biến toàn cục chia sẻ giữa các lần gọi.

### Một giá trị có thể chiếm một hoặc hai slot

| Loại tính toán JVM | Độ rộng category | Độ sâu operand stack khi đẩy một giá trị | Local slot cần dùng |
| --- | --- | ---: | ---: |
| `int`, `float`, `reference` | Category 1 | 1 | 1 |
| `long`, `double` | Category 2 | 2 | 2 |
| `byte`, `short`, `char`, `boolean` khi thao tác tính toán bằng bytecode | Thường theo `int` (category 1) | 1 | 1 cho local kiểu tính toán tương ứng |

`long`/`double` là **một giá trị logic** nhưng cần hai slot theo mô hình JVM; không phải hai số độc lập để code tùy ý đọc riêng từng nửa. Trên operand stack, các lệnh thao tác stack như `dup`, `dup2`, `pop`, `pop2` phải tôn trọng phân loại category này.

## 2. Các cách đẩy hằng số trực tiếp lên operand stack

JVM có một số opcode ngắn cho các hằng số phổ biến, giúp bytecode nhỏ hơn khi không cần truy cập constant pool.

| Lệnh bytecode | Giá trị đẩy lên stack | Loại | Ghi chú |
| --- | --- | --- | --- |
| `aconst_null` | Tham chiếu `null` | Reference, category 1 | Không tạo object |
| `iconst_m1` | `-1` | `int` | Opcode riêng cho âm một |
| `iconst_0` … `iconst_5` | `0` … `5` | `int` | Sáu opcode cho int nhỏ |
| `lconst_0`, `lconst_1` | `0L`, `1L` | `long`, category 2 | Đẩy một giá trị dài chiếm hai slot |
| `fconst_0` … `fconst_2` | `0.0f`, `1.0f`, `2.0f` | `float` | Ba hằng số float có sẵn |
| `dconst_0`, `dconst_1` | `0.0d`, `1.0d` | `double`, category 2 | Hai hằng số double có sẵn |
| `bipush n` | Số nguyên từ `-128` đến `127` | `int` | Immediate **signed 8-bit**, được sign-extend thành int |
| `sipush n` | Số nguyên từ `-32768` đến `32767` | `int` | Immediate **signed 16-bit**, được sign-extend thành int |

Điểm quan trọng: chữ **byte** trong `bipush` mô tả độ rộng *immediate operand trong tệp class*, **không** có nghĩa opcode này đẩy giá trị Java `byte` lên stack. Cả `bipush` và `sipush` đều đẩy **`int`**.

Ví dụ các hiệu ứng stack, với ký hiệu phần bên trái là stack trước lệnh:

```text
[]             -- iconst_3   --> [3]
[]             -- bipush 7   --> [7]
[]             -- sipush 300 --> [300]
[12]           -- iconst_2   --> [12, 2]
[12, 2]        -- iadd       --> [14]
[]             -- lconst_1   --> [1L]  // một giá trị long = hai slot
[]             -- aconst_null --> [null]
```

Trong ví dụ này, `iadd` yêu cầu hai giá trị `int` trên đỉnh stack; nó lấy chúng ra rồi đẩy tổng `int`. Việc `sipush 300` có operand mã hóa bằng hai byte không làm cho kết quả trở thành kiểu `short`. Tương tự, `lconst_1` không phải hai phần tử `int` có thể cộng bằng `iadd`.

### Giá trị vượt các immediate nhỏ thì sao?

Khi giá trị không phù hợp opcode immediate, compiler có thể chọn lệnh truy cập **run-time constant pool**, ví dụ `ldc` hoặc `ldc2_w`. Sự lựa chọn opcode giữa các cách biểu diễn hợp lệ phụ thuộc compiler; hằng số xuất hiện trong source không đảm bảo phải được đưa lên operand stack ở runtime (compiler có thể thực hiện constant folding hoặc tối ưu code).

## 3. `ldc`, `ldc_w` và `ldc2_w`: đọc dữ liệu từ run-time constant pool

Run-time constant pool gắn với class chứa các constant và symbolic references được định nghĩa qua cấu trúc constant pool trong tệp `.class`. Ba opcode dùng **index** vào constant pool:

| Lệnh | Operand chỉ số | Các loại thường nạp | Số byte của lệnh tính cả opcode |
| --- | --- | --- | ---: |
| `ldc index` | Chỉ số `u1` không dấu, tối đa 255 | `int`, `float`, `String` reference, `Class` reference, method type/handle hoặc dynamic constant category 1 | 2 |
| `ldc_w indexbyte1 indexbyte2` | Chỉ số `u2` không dấu, tối đa 65535 | Tương tự `ldc`; chỉ **khác độ rộng index** | 3 |
| `ldc2_w indexbyte1 indexbyte2` | Chỉ số `u2` không dấu | `long`, `double` hoặc dynamic constant tương ứng category 2 | 3 |

- `ldc_w` **không** có nghĩa “load giá trị 64-bit”; chữ `_w` ám chỉ **wider constant-pool index**. Nó có thể nạp `int`, `float` hoặc reference giống `ldc`.
- `ldc2_w` được sử dụng cho các giá trị `long`/`double` ngoài những hằng số có opcode riêng; không tồn tại opcode chuẩn tên `ldc2` có index 8-bit.
- `String` literal được nạp thành **reference** trỏ tới đối tượng `String` tương ứng; `ldc` không phải phép sao chép từng ký tự lên operand stack.
- Với constant `Class`, method handle/type hoặc **dynamic constant**, quá trình resolution có thể xảy ra và phát sinh lỗi liên kết theo đặc tả. Đừng coi mọi `ldc` chỉ là “đọc một số đã lưu” theo nghĩa đơn giản.
- `ldc` có thể nạp constant category 1 được tính động (`CONSTANT_Dynamic`); `ldc2_w` có thể nạp dynamic constant `long`/`double`. Không phải Java source bình thường nào cũng trực tiếp biểu diễn được các constant này; chúng có thể đến từ compiler/library tạo bytecode.

Ví dụ theo lựa chọn `javac` Java 17 thông dụng:

```text
iconst_5               // int 5
bipush 100             // int 100
sipush 300             // int 300
ldc #N                 // int 40000 từ constant pool
ldc2_w #M              // long 5000000000 từ constant pool
ldc #K                 // String "JVM" từ constant pool
```

`#N`, `#M`, `#K` là **chỉ số minh họa**, không phải số bắt buộc. Chỉ số thật phụ thuộc thứ tự nội dung trong constant pool mà compiler tạo.

### Vì sao một dòng source không tương ứng một `ldc`?

`static final int LIMIT = 100;` có thể là **compile-time constant** và được compiler nhúng trực tiếp tại nơi sử dụng. Một biểu thức chỉ gồm constant có thể được tính trước, khiến bytecode không còn opcode cộng/nhân tương ứng. Khi quan sát bytecode, chỉ kết luận về **class file đang xem**; hợp đồng ngôn ngữ vẫn nằm ở JLS và ngữ nghĩa opcode nằm ở JVMS.

## 4. Lệnh nạp local variables lên operand stack

Các lệnh `load` lấy giá trị trong **local-variable array của frame hiện tại** rồi **đẩy bản sao của giá trị lên operand stack**.

| Loại JVM | Dạng chỉ số tường minh | Dạng viết tắt cho index 0–3 | Hiệu ứng |
| --- | --- | --- | --- |
| `int` | `iload index` | `iload_0` … `iload_3` | Locals[index] → stack (1 slot) |
| `long` | `lload index` | `lload_0` … `lload_3` | Locals[index,index+1] → stack (2 slot) |
| `float` | `fload index` | `fload_0` … `fload_3` | Locals[index] → stack (1 slot) |
| `double` | `dload index` | `dload_0` … `dload_3` | Locals[index,index+1] → stack (2 slot) |
| Reference | `aload index` | `aload_0` … `aload_3` | Locals[index] → stack (1 slot) |

Một số quy tắc quan trọng:

1. `iload_0` là một opcode riêng có **chỉ số 0 ngầm định**; `iload 0` sử dụng thêm một byte operand cho index. Hai cách có cùng ngữ nghĩa, khác encoding.
2. `lload_2` bắt đầu ở **slot 2**, chiếm slot **2 và 3**, rồi đẩy một giá trị `long` dùng hai slot lên stack.
3. Các slot locals phải chứa **kiểu hợp lệ** theo trạng thái verification. `iload` không thể tùy tiện đọc reference, `aload` không thể đọc `int`, và không được dùng giá trị local chưa được gán hợp lệ.
4. Các kiểu Java `byte`, `short`, `char`, `boolean` trong nhiều thao tác local được JVM biểu diễn qua **`int`** và lệnh `iload`. Khi cần bảo đảm giá trị nằm trong miền hẹp, compiler có thể phát sinh chuyển đổi như `i2b`, `i2s`, `i2c`.
5. `aload_0` trong method instance **thường** nạp `this` vì receiver ở local slot 0; trong method `static`, slot 0 có thể chứa tham số đầu tiên thay vì `this`.

Không suy luận rằng `iload_1` nghĩa là “nạp tham số thứ hai” trong mọi method: receiver ẩn và các tham số category 2 làm thay đổi thứ tự **slot**.

## 5. Lệnh lưu từ operand stack về local variables

`store` làm thao tác ngược `load`: **pop giá trị đỉnh stack** và lưu vào vị trí được đánh index trong local variables.

| Loại JVM | Dạng chỉ số tường minh | Dạng viết tắt index 0–3 | Hiệu ứng |
| --- | --- | --- | --- |
| `int` | `istore index` | `istore_0` … `istore_3` | stack pop (int) → locals[index] |
| `long` | `lstore index` | `lstore_0` … `lstore_3` | stack pop (long) → locals[index,index+1] |
| `float` | `fstore index` | `fstore_0` … `fstore_3` | stack pop (float) → locals[index] |
| `double` | `dstore index` | `dstore_0` … `dstore_3` | stack pop (double) → locals[index,index+1] |
| Reference | `astore index` | `astore_0` … `astore_3` | stack pop (reference) → locals[index] |

Ví dụ biểu thức `int result = a + b;` trong một method có thể được chuyển thành:

```text
iload_0     // [] → [a]
iload_1     // [a] → [a, b]
iadd        // [a, b] → [a+b]
istore_2    // [a+b] → []; locals[2] nhận a+b
```

Ở đây, `istore_2` **không** trả kết quả từ method: chỉ ghi biến cục bộ. Để trả `int`, bytecode có thể tiếp tục `iload_2` rồi `ireturn`. Một compiler khác có thể bỏ hẳn store/load trung gian và trả kết quả trực tiếp mà vẫn đúng nghĩa Java.

Một giới hạn khác: gán một local category 2 (`long`/`double`) làm thay đổi trạng thái hợp lệ của cả cặp slot nó chiếm. Các phép store đè lên một phần cặp slot category 2 có thể khiến giá trị category 2 trước đó không còn đọc được hợp lệ. Vì vậy mọi code tự tạo/sửa bytecode phải đồng bộ **loại và phạm vi sống của local slots**, không chỉ nhìn số index.

### `max_locals` khác số biến viết bằng tên

- Method `static int add(int a, int b)`: `a` ở slot 0, `b` ở slot 1, nếu lưu kết quả vào biến mới thì có thể dùng slot 2.
- Method `int add(int b)` là instance: `this` ở slot 0, `b` ở slot 1.
- Method `static long add(long a, long b)`: `a` chiếm slot 0–1, `b` chiếm slot 2–3. Kết quả lưu thêm `long` có thể cần slot 4–5.
- Compiler có thể **tái sử dụng** một slot sau khi giá trị cũ hết phạm vi cần thiết hoặc tạo slot cho giá trị trung gian không có tên trong source.

Trong `javap -v`, dòng `stack=..., locals=..., args_size=...` tóm tắt thông tin frame; `args_size` là cách `javap` trình bày số slot dành cho các tham số JVM của method (bao gồm receiver cho method instance và hai slot cho mỗi `long`/`double`), chứ **không** phải một trường riêng của cấu trúc `Code_attribute`.

## 6. Ví dụ Java 17 chạy được: constant → stack → local → phép toán

Tạo file **`LocalStackFlow.java`**:

```java
public class LocalStackFlow {
    public static int calculate(int input) {
        int increased = input + 7;
        int result = increased + 40_000;
        return result;
    }

    public static long addLong(long first, long second) {
        long sum = first + second;
        return sum;
    }

    public static String label() {
        return "JVM";
    }

    public static void main(String[] args) {
        System.out.println("calculate=" + calculate(2));
        System.out.println("long=" + addLong(20L, 22L));
        System.out.println("label=" + label());
    }
}
```

Biên dịch và chạy trên JDK có `javac` hỗ trợ Java 17:

```bash
javac --release 17 -g LocalStackFlow.java
java LocalStackFlow
javap -c -v -p LocalStackFlow
```

**Stdout của lệnh `java LocalStackFlow`**:

```text
calculate=40009
long=42
label=JVM
```

### Đọc method `calculate(int)` qua `javap`

Với `javac` Java 17 thông thường, phần opcode đáng chú ý có thể xuất hiện như sau (chỉ trích method này, bỏ qua các mục metadata khác):

```text
public static int calculate(int);
  Code:
    stack=2, locals=3, args_size=1
       0: iload_0
       1: bipush        7
       3: iadd
       4: istore_1
       5: iload_1
       6: ldc           #...   // int 40000
       8: iadd
       9: istore_2
      10: iload_2
      11: ireturn
```

Dấu `#...` ở trên đại diện cho **một index constant pool**, không phải toàn văn output có thể copy để thực thi. Bytecode index `0, 1, 3, 4, ...` là **offset tính theo byte**, không phải số lệnh. `bipush 7` chiếm hai byte; `ldc` với index một byte cũng chiếm hai byte.

| BCI / opcode | Local variables (slot 0, 1, 2) | Operand stack, đáy → đỉnh | Số slot trên stack |
| --- | --- | --- | ---: |
| Trước `iload_0` | `[2, -, -]` | `[]` | 0 |
| Sau `iload_0` | `[2, -, -]` | `[2]` | 1 |
| Sau `bipush 7` | `[2, -, -]` | `[2, 7]` | 2 |
| Sau `iadd` | `[2, -, -]` | `[9]` | 1 |
| Sau `istore_1` | `[2, 9, -]` | `[]` | 0 |
| Sau `iload_1` | `[2, 9, -]` | `[9]` | 1 |
| Sau `ldc 40000` | `[2, 9, -]` | `[9, 40000]` | 2 |
| Sau `iadd` | `[2, 9, -]` | `[40009]` | 1 |
| Sau `istore_2` | `[2, 9, 40009]` | `[]` | 0 |
| Sau `iload_2` | `[2, 9, 40009]` | `[40009]` | 1 |
| `ireturn` | Frame sắp trả về | Trả `40009` cho caller | — |

Dấu `-` ở bảng nghĩa là local chưa có giá trị cần dùng theo đường đi minh họa, không phải giá trị Java `null`. Độ sâu lớn nhất **2 slot**, nên `max_stack=2` hợp lý cho method này; cần **3 slots locals** (input/increased/result), nên `max_locals=3`.

Lưu ý: đây là **một cách biên dịch**. Compiler được phép biến đổi hoặc loại bỏ local trung gian, thay đổi việc nạp hằng số miễn code kết quả tuân theo JLS/JVMS. Khi học quy tắc lệnh, hãy kiểm tra bytecode thực tế từ chính compiler đang dùng.

### Với `addLong(long,long)`: hai giá trị nhưng bốn slots

Trong method `static` này:

```text
locals:
  slot 0-1 : first  = 20L
  slot 2-3 : second = 22L
  slot 4-5 : sum    = 42L (sau lstore)

operand stack:
  []                         ban đầu
  [20L]                      sau lload_0         (2 slot)
  [20L, 22L]                 sau lload_2         (4 slot)
  [42L]                      sau ladd            (2 slot)
  []                         sau lstore 4        (0 slot)
  [42L]                      sau lload 4         (2 slot)
  []                         sau lreturn
```

`max_stack` của dạng trên là **4**, `max_locals` là **6**. Đừng đếm `[20L, 22L]` thành “hai slots”: đó là **hai giá trị category 2**, tổng cộng bốn slots.

`label()` minh họa nạp String literal bằng `ldc` vào operand stack rồi trả về bằng `areturn`; JVM thao tác trên **reference** chứ không chèn từng byte UTF-8 của chuỗi vào stack.

## 7. Ví dụ về tham số, `this` và vị trí local không liên tiếp

File độc lập **`ParameterSlots.java`**:

```java
public class ParameterSlots {
    private final int base;

    public ParameterSlots(int base) {
        this.base = base;
    }

    public long combine(int count, long amount, double ignored) {
        long result = amount + count + base;
        return result;
    }

    public static void main(String[] args) {
        ParameterSlots value = new ParameterSlots(5);
        System.out.println("combined=" + value.combine(2, 30L, 0.5));
    }
}
```

Lệnh:

```bash
javac --release 17 -g ParameterSlots.java
java ParameterSlots
javap -c -p ParameterSlots
```

**Stdout của `java`**:

```text
combined=37
```

Trong **instance method** `combine(int,long,double)`, chỉ số locals ban đầu được xác định theo descriptor, dù biến `ignored` không xuất hiện trong thân method:

| Slot | Nội dung tại đầu method |
| --- | --- |
| 0 | Reference `this` |
| 1 | `count` (`int`) |
| 2 và 3 | `amount` (`long`) |
| 4 và 5 | `ignored` (`double`) |
| 6 và 7 | Có thể được compiler dùng cho `result` (`long`) |

Lệnh `iload_1` lấy `count`; `lload_2` lấy `amount`; `aload_0` lấy `this` để đọc field `base` qua `getfield`; một kết quả `long` có thể được lưu bằng `lstore 6`. Các vị trí tham số là bắt buộc theo quy ước invocation; local tạm cho `result` và chuỗi opcode cụ thể do compiler quyết định.

Các ví dụ có `long`/`double` cho thấy vì sao `lload_2` **không phải** “load tham số thứ ba” và vì sao không thể tính `max_locals` chỉ bằng số tham số viết trong Java source.

## 8. `iinc` cập nhật local mà không đi qua operand stack

`iinc index, const` là opcode đặc biệt thực hiện cộng một immediate **signed byte** vào một local variable kiểu `int`. Nó cập nhật locals tại `index` **trực tiếp**; operand stack **không thay đổi**.

Ví dụ minh họa bytecode cho biến cục bộ `int counter`:

```text
locals[1] = 8;  stack = []
iinc 1, 1
locals[1] = 9;  stack = []
```

So sánh phép tăng viết thành load/add/store:

```text
iload_1       // [] → [8]
iconst_1      // [8] → [8, 1]
iadd          // [8, 1] → [9]
istore_1      // [9] → []; locals[1] = 9
```

Hai chuỗi có thể tương đương về kết quả trong một ngữ cảnh đơn giản, nhưng `iinc` chỉ áp dụng cho **local int** và có operand immediate theo encoding. Không có opcode `linc` tương đương cho `long`, và việc cập nhật field/array không diễn ra bằng `iinc`.

Ví dụ Java:

```java
public static int next(int value) {
    value++;
    return value;
}
```

Đây là **đoạn method minh họa**, đặt trong một class để biên dịch. `javac` Java 17 thường sinh `iinc 0, 1` rồi `iload_0` và `ireturn`. Nếu `value == Integer.MAX_VALUE`, phép tăng nguyên wrap thành `Integer.MIN_VALUE`; `iinc` không ném exception do overflow.

## 9. `wide`: mở rộng chỉ số local và immediate của `iinc`

Các lệnh `iload index`/`istore index` ở dạng thường có **index 8 bit** không dấu. Khi cần tham chiếu local slot index lớn hơn 255, JVM dùng prefix **`wide`** với index **16 bit**. `wide` hỗ trợ các nhóm load/store locals, `ret` cũ và `iinc`; nó không phải một opcode phổ dụng mở rộng mọi lệnh.

Dạng minh họa:

```text
iload 12          // chỉ số 12 dùng operand 1 byte
wide iload 300    // chỉ số 300 dùng operand 2 byte
iinc 7, -3        // index 1 byte; const signed 1 byte
wide iinc 300, 1000 // index 2 byte; const signed 2 byte
```

**Đây là mnemonic minh họa format**, không phải đoạn `javap` lấy từ một file xác định. Khi mã hóa thành bytes, `wide` là một prefix có opcode riêng đứng trước opcode được phép mở rộng.

- Với `wide iload`, `wide istore` và các load/store khác, chỉ số local thành `u2` (0–65535, nhưng còn phải nằm trong `max_locals`).
- Với `wide iinc`, cả index là `u2` và immediate là số nguyên **signed 16-bit**.
- `wide` **không** làm `int` thành `long`, không làm operand stack rộng hơn và không mở rộng một constant-pool index: việc đó là nhiệm vụ `ldc_w`.

Thêm một lưu ý thực tế: chương trình source có rất nhiều biến cục bộ có thể khiến compiler sinh `wide`, nhưng compiler có thể tái sử dụng local slots. Số biến trong source không tự xác định sự xuất hiện của prefix này.

## 10. Nạp/lưu local khác với đọc/ghi field và array

Có ba nơi thường bị gọi chung là “biến” khi đọc Java source, nhưng opcode khác nhau:

| Trong source | Nhóm opcode đại diện | Đối tượng chứa dữ liệu |
| --- | --- | --- |
| Biến cục bộ hoặc tham số | `iload`/`istore`, `aload`/`astore`, v.v. | Local-variable array của frame |
| Trường instance | `getfield`/`putfield` | Một object được chỉ ra bằng reference |
| Trường static | `getstatic`/`putstatic` | Field static của class/interface |
| Phần tử array | `iaload`/`iastore`, `aaload`/`aastore`, `baload`/`bastore`... | Array object và index |

Ví dụ, với `int[] a`, `a[0] = 9` **không** được thực hiện bằng `istore_0` trừ trường hợp compiler đang gán 9 cho *biến local* nào đó. Array store cần reference tới array, index và giá trị trên operand stack, rồi dùng `iastore`.

Tương tự, lệnh `aload_0` thường đẩy `this`; để đọc `this.base`, bytecode cần thêm `getfield`. `aload`/`astore` thao tác **reference**, không tự copy toàn bộ trạng thái object.

### Ví dụ minh họa array, với luồng stack

Giả sử locals slot 0 chứa reference tới `int[]` và muốn ghi phần tử đầu tiên bằng 9:

```text
[]                     // operand stack ban đầu
aload_0                // [arrayRef]
iconst_0               // [arrayRef, 0]
bipush 9               // [arrayRef, 0, 9]
iastore                // []; arrayRef[0] = 9
```

`iastore` lấy ra ba toán hạng từ stack; `istore` chỉ lấy một giá trị `int` rồi ghi vào local. Sự khác biệt này rất quan trọng khi xem trace hoặc tính `max_stack`.

## 11. `dup`, `pop` và hậu quả với độ sâu operand stack

Không phải mọi lệnh thao tác stack đều làm số học hoặc truy cập local. Một số lệnh thay đổi **cách giá trị nằm trên stack**:

- `pop` bỏ một giá trị **category 1** ở đỉnh.
- `pop2` bỏ một giá trị category 2 **hoặc** hai giá trị category 1 ở đỉnh, tùy cấu hình hợp lệ.
- `dup` nhân đôi giá trị category 1 ở đỉnh.
- `dup2` có các dạng hợp lệ để nhân đôi một giá trị category 2 hoặc cặp giá trị category 1.
- `swap` đổi chỗ hai giá trị category 1 ở đỉnh; không dùng để tùy ý hoán đổi một `long` và một `int`.

Ví dụ, biểu thức có cả gán và sử dụng kết quả có thể cần `dup` để **vừa lưu vào một local/field vừa giữ một bản giá trị trên stack**. Đọc opcode mà không theo dõi stack sẽ rất dễ nhầm hai bản sao thành hai object khác nhau; với reference, `dup` sao chép **tham chiếu**, không tạo object mới.

## 12. Kiểu dữ liệu, verification và lỗi liên quan

JVM verifier kiểm tra rằng code trong method có các đường control flow và kiểu stack/locals hợp lệ theo class-file version tương ứng. Những ví dụ bytecode **không hợp lệ** về kiểu:

```text
aconst_null
iadd                  // sai: iadd cần hai int

iconst_1
lstore_0              // sai: lstore cần một long category 2

iload_1               // sai nếu slot 1 chưa có giá trị int hợp lệ
```

Đây là **các fragment cố ý sai để minh họa verification**, tuyệt đối không phải chương trình chạy được. Lỗi kiểu/stack có thể dẫn tới `VerifyError`; tệp class hỏng cấu trúc có thể dẫn tới `ClassFormatError`. Những lỗi này khác `NullPointerException` hoặc `ArithmeticException` phát sinh khi **code hợp lệ** đang thực thi.

Những quy tắc cần giữ khi đọc/tạo bytecode:

1. Phải có đủ operand theo đúng **thứ tự và loại** cho opcode tiếp theo.
2. Các nhánh hội tụ cần tương thích về **loại stack/locals**; `StackMapTable` có thể hỗ trợ verifier xác nhận.
3. Không được load local trước khi nó được gán hợp lệ trên đường thực thi đi tới lệnh ấy.
4. `max_stack` phải đủ cho mọi đường thực thi; `max_locals` phải bao trùm tất cả index và cặp index+1 của category 2.
5. Khi chuyển kiểu nhỏ, nhớ `byte`/`short`/`char` được tính dưới dạng `int` và có thể có `i2b`/`i2s`/`i2c` khi cần thu hẹp.
6. Với `long`/`double`, một giá trị logic có hai slot; không thể truy cập “nửa trên” bằng `iload` để giả làm số `int`.

Một giá trị `null` được `aconst_null` đẩy lên operand stack là **reference hợp lệ**. Chỉ khi dùng nó trong các thao tác yêu cầu object thực (ví dụ dereference qua `getfield`/`invokevirtual`) mới có thể gặp `NullPointerException`; bản thân `aconst_null` không ném lỗi.

## 13. Đọc kết quả `javap` theo một quy trình nhất quán

Sử dụng `javap -c -v -p FileName` để xem method bytecode và metadata liên quan. Khi gặp một đoạn không hiểu:

1. **Xác định đúng method descriptor**: static hay instance, tham số nào chiếm một/hai slot, có receiver `this` không.
2. **Đọc `stack=..., locals=..., args_size=...`**: `max_stack` là độ sâu slot lớn nhất; `max_locals` là số slot được cấp cho locals; `args_size` là số slot đối số mà `javap` diễn giải.
3. **Khởi tạo bảng locals**: đặt slot cho receiver và các tham số theo descriptor. Đánh dấu slot chưa khởi tạo/không hợp lệ.
4. **Đi theo BCI**: với `iconst_*`/`bipush`/`ldc`, đẩy giá trị; với `iload`, sao chép từ local; với `istore`, pop về local.
5. **Giữ đúng category 1/2** khi đếm stack, đặc biệt với `lload`, `dload`, `lstore`, `dstore`.
6. **Theo dõi thao tác tính toán, gọi, array, field và return**: chúng tiêu thụ/đẩy số lượng operand khác nhau.
7. **Đối chiếu các nhánh và handler**: một đường đi có thể không giống đường đi “thẳng” của output; handler bắt đầu với exception reference trên operand stack.
8. **Phân biệt observation và specification**: compiler có thể chọn opcode khác cho cùng source, nhưng ngữ nghĩa của từng opcode thực thi được JVMS quy định.

### Bảng đối chiếu nhanh

| Nếu thấy trong `javap` | Đọc như sau | Điều không nên suy ra |
| --- | --- | --- |
| `iconst_2` | Đẩy int 2 lên stack | Có một biến Java tên “2” |
| `bipush 100` | Đẩy int 100 từ immediate 8-bit | Nó đẩy một `byte` Java lên stack |
| `ldc #17` | Nạp một constant category 1 qua pool index 17 | Index 17 có nghĩa giá trị hằng số 17 |
| `ldc_w #300` | Nạp qua index 16-bit | Giá trị vừa nạp có kích thước 64 bit |
| `ldc2_w #42` | Nạp `long`/`double` hoặc dynamic constant tương ứng | Chữ 42 chính là giá trị `long` |
| `iload_0` | Sao chép int từ local slot 0 lên stack | Nó xóa slot 0 |
| `istore_1` | Pop int từ stack vào local slot 1 | Nó trả kết quả cho caller |
| `lload_2` | Load long bắt đầu ở slot 2–3 | Long có hai số nguyên độc lập |
| `iinc 1, 1` | Tăng local int slot 1, không thay operand stack | Nó tương đương với phép tăng field |
| `wide iload 300` | Mở rộng encoding của local index | Mở rộng giá trị int thành long |
| `aload_0; getfield` | Nạp reference, rồi đọc field của object | `aload_0` tự đọc toàn bộ object |
| `iaload` | Đọc phần tử array `int` | Đọc local variable bằng `iload` |

## 14. Các lỗi thường gặp

**Lỗi 1 — Nhầm locals là stack.** Nói “`istore` đưa giá trị lên stack” là ngược: `store` lấy giá trị khỏi operand stack rồi ghi vào local.

**Lỗi 2 — Đếm giá trị, bỏ qua số slot.** `[10L,20L]` là hai giá trị category 2 nhưng sâu **4 slots**, không phải 2. Cần đếm đúng để hiểu `max_stack`.

**Lỗi 3 — Nhầm `ldc_w` với `ldc2_w`.** Một lệnh mở rộng **chỉ số constant pool**; lệnh còn lại nạp hằng kiểu `long`/`double` (hoặc constant động cùng loại).

**Lỗi 4 — Cho rằng `byte` được nạp bằng `bload`.** JVM không có nhóm load locals `bload`; `byte` thường được biểu diễn bằng `int` khi đi qua stack/local.

**Lỗi 5 — Cho rằng local slot index bằng số thứ tự tham số.** `this` ở slot 0 với instance method; `long`/`double` chiếm hai slot nên các index không nhất thiết tăng một cho mỗi tham số.

**Lỗi 6 — Đồng nhất `iload` với `iaload`.** `iload` đọc **locals**; `iaload` đọc **array element** và tiêu thụ array reference cùng index trên stack.

**Lỗi 7 — Giả định mọi hằng số phải xuất hiện qua `ldc`.** Các giá trị nhỏ có `iconst_*`/`bipush`/`sipush`; compiler có thể fold các biểu thức hằng.

**Lỗi 8 — Dùng `wide` với bất kỳ opcode.** Nó chỉ hợp lệ khi đứng trước các opcode được JVMS định nghĩa hỗ trợ, không phải “prefix tăng độ rộng” tổng quát.

**Lỗi 9 — Lấy fragment `javap` làm cam kết compiler.** Một compiler khác có thể tái sử dụng locals, loại bỏ biến tạm hoặc dùng một encoding khác. Hãy học **ngữ nghĩa lệnh** và xác nhận trên class đang phân tích.

## 15. Bài tự kiểm tra

**Câu A.** Một method static có `int x` tại local 0 và `int y` tại local 1. Chuỗi nào tính rồi lưu `x+y` vào local 2?

```text
iload_0
iload_1
iadd
istore_2
```

Sau `istore_2`, stack trở lại rỗng và local 2 chứa tổng.

**Câu B.** `ldc_w #300` và `ldc2_w #300` có cùng ý nghĩa vì index đều là 300?

Không. `ldc_w` có index rộng nhưng nạp constant category 1; `ldc2_w` nạp `long`/`double` category 2. Index giống nhau không cho phép tùy ý đổi opcode.

**Câu C.** Sau `lload_0` và `lload_2`, stack chứa hai `long`. Độ sâu tính theo slot?

**4 slot**.

**Câu D.** `iinc 1, 5` có pop hay push operand stack?

**Không**; nó sửa local `int` slot 1 trực tiếp.

**Câu E.** Trong instance method `m(int a, long b, double c)`, tham số `c` bắt đầu ở local slot mấy?

`this` ở 0, `a` ở 1, `b` ở 2–3, nên `c` bắt đầu ở **slot 4** (chiếm 4–5).

**Câu F.** `bipush 127` đẩy kiểu `byte` hay `int`?

**`int`**, do immediate signed byte được sign-extend trước khi đẩy lên operand stack.

## Tài liệu gốc theo Java SE 17

- [JVMS 2.6 — Frames, local variables, operand stacks](https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-2.html#jvms-2.6): mô hình frame của từng lần gọi method.
- [JVMS `2.11.1–`2.11.2 — Types và load/store instructions](https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-2.html#jvms-2.11.2): loại category 1/2, nhóm load/store locals và constant loading.
- [JVMS 4.7.3 — Code attribute](https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-4.html#jvms-4.7.3): định nghĩa `max_stack`, `max_locals`, `code_length` và bảng exception.
- [JVMS 6.5 — Descriptions of individual instructions](https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-6.html#jvms-6.5): định nghĩa `bipush`, `sipush`, `ldc`, `ldc_w`, `ldc2_w`, `iload`, `istore`, `lload`, `iinc`, `wide`, `dup`, `pop`.
- [JVMS 4.4 — Constant pool](https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-4.html#jvms-4.4): các dạng constant pool entry.
- [Hướng dẫn `javap` Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/specs/man/javap.html): sử dụng `-c`, `-v`, `-p` để phân tích class file thực tế.

**Ghi nhớ:** hằng số được **đẩy lên stack**; `load` sao chép từ locals **lên stack**; `store` pop từ stack **về locals**. Để đọc một instruction chính xác, phải biết **kiểu**, **số slot**, **local index**, **các giá trị hiện có trên operand stack**, và lệnh tiếp theo sẽ lấy/đẩy bao nhiêu giá trị.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="bytecode-instructions-003">Nhóm lệnh số học, bit, so sánh và chuyển kiểu</a>

<details>
<summary>Xem chi tiết</summary>

# Nhóm lệnh bytecode JVM: số học, bit, so sánh và chuyển kiểu

## Mục tiêu

Bytecode của Java Virtual Machine (JVM) là tập lệnh được định nghĩa trong *Java Virtual Machine Specification (JVMS)*. Người lập trình thường viết toán tử Java như `+`, `&`, `<` hay ép kiểu `(int) x`; compiler có thể chuyển chúng thành các lệnh JVM tương ứng để thao tác trên **operand stack** và **local variables**. Một toán tử nguồn không luôn tương đương đúng một opcode vì còn có nâng kiểu, nạp/lưu dữ liệu, rẽ nhánh, lời gọi thư viện và tối ưu.

Sau bài học, bạn có thể:

- Đọc tên và quy tắc kiểu của opcode số học, phép bit, phép dịch, so sánh và chuyển đổi nguyên thủy trên JVM.
- Giải thích cách opcode lấy toán hạng từ operand stack, đẩy kết quả và dùng local variables.
- Phân biệt tràn số nguyên với số thực, dịch phải có dấu với không dấu, toán tử so sánh với instruction rẽ nhánh và cách xử lý NaN.
- Hiểu mở rộng/thu hẹp kiểu, mất độ chính xác, truncation và các trường hợp NaN/vô cực khi ép kiểu.
- Tự biên dịch Java 17+, xem bytecode qua `javap -c -p`, so sánh kết quả thực thi với quy tắc JVM.

## 1. Đọc quy tắc đặt tên lệnh và mô hình stack

Nhiều opcode có **tiền tố kiểu dữ liệu** và phần **hậu tố phép toán**:

| Tiền tố | Kiểu giá trị trên operand stack | Ví dụ |
| --- | --- | --- |
| `i` | `int` (cũng dùng làm representation cho `boolean`, `byte`, `short`, `char` trong nhiều thao tác) | `iadd`, `iand`, `ishl`, `i2d` |
| `l` | `long` | `ladd`, `lcmp`, `lushr` |
| `f` | `float` | `fadd`, `fcmpl`, `f2i` |
| `d` | `double` | `ddiv`, `dcmpg`, `d2l` |

Ngoài các tiền tố đó còn có opcode như `iinc`, `if_icmplt` hoặc `if_acmpeq` với vai trò đặc thù. JVM **không có nguyên một bộ opcode số học riêng cho `byte`/`short`/`char`/`boolean`**; khi cần, compiler thực hiện các phép toán theo **numeric promotion** của ngôn ngữ Java và dùng `int` hoặc opcode chuyển đổi để bảo toàn ý nghĩa.

### Operand stack hoạt động theo nguyên tắc LIFO

Ví dụ giả định operand stack đang có hai số nguyên:

~~~text
Trước iadd:      ..., value1, value2  (value2 ở đỉnh)
Thực thi iadd:   pop value2, pop value1; tính value1 + value2
Sau iadd:        ..., result
~~~

Với phép không giao hoán, thứ tự rất quan trọng: `isub` tính **value1 − value2**, `idiv` tính **value1 / value2**, không đảo chiều hai toán hạng.

Trong phần mô tả opcode của JVMS, mẫu `..., value1, value2 → ..., result` là **mô hình stack**, không phải cú pháp Java có thể biên dịch. Các kiểu `long` và `double` thuộc **category 2** (chiếm hai đơn vị độ sâu stack), còn `int` và `float` thuộc **category 1**. Bytecode verifier kiểm tra kiểu và hình dạng stack trên những đường điều khiển hợp lệ.

### Local variables và bytecode số học

Ví dụ Java đầy đủ về phương thức:

~~~java
public static int add(int left, int right) {
    return left + right;
}
~~~

Đây là **một phương thức**, chưa phải class chạy độc lập. Với `javac` thông thường, đoạn bytecode tương ứng có dạng:

~~~text
0: iload_0
1: iload_1
2: iadd
3: ireturn
~~~

Hai lệnh `iload` nạp biến cục bộ vào stack, `iadd` tính tổng, `ireturn` trả giá trị về caller. Đừng nhầm `iadd` với thao tác trực tiếp trên hai ô local variables; phép cộng lấy hai giá trị **trên operand stack**. Compiler khác có thể sinh chuỗi tương đương, và các chỉ số/offset bytecode không phải phần của ngữ nghĩa chương trình Java.

## 2. Nhóm số học: `add`, `sub`, `mul`, `div`, `rem` và `neg`

Mỗi hàng có các biến thể tương ứng với `int`, `long`, `float`, `double`:

| Phép toán | `int` | `long` | `float` | `double` |
| --- | --- | --- | --- | --- |
| Cộng | `iadd` | `ladd` | `fadd` | `dadd` |
| Trừ | `isub` | `lsub` | `fsub` | `dsub` |
| Nhân | `imul` | `lmul` | `fmul` | `dmul` |
| Chia | `idiv` | `ldiv` | `fdiv` | `ddiv` |
| Lấy dư | `irem` | `lrem` | `frem` | `drem` |
| Đổi dấu | `ineg` | `lneg` | `fneg` | `dneg` |

Các lệnh nhị phân pop hai toán hạng theo kiểu tương ứng rồi push một kết quả. Các opcode đổi dấu là đơn toán hạng: `..., value → ..., result`. Không có opcode cộng hoặc nhân trực tiếp cho hai đối tượng `BigInteger` hay `BigDecimal`; phép đó thực hiện qua lời gọi phương thức thư viện.

### Tràn số nguyên: modulo theo độ rộng bit

`int` có 32 bit có dấu và `long` có 64 bit có dấu. Các lệnh cộng/trừ/nhân số nguyên dùng hai bit-width cố định; nếu kết quả toán học vượt giới hạn biểu diễn, các bit cao bị loại bỏ. Kết quả tương ứng biểu diễn hai's complement; **không tự ném `ArithmeticException` vì overflow**.

~~~java
int a = Integer.MAX_VALUE;
System.out.println(a + 1); // -2147483648
System.out.println(-Integer.MIN_VALUE); // -2147483648
~~~

Đây là snippet trong phương thức Java. Ví dụ thứ hai cho thấy đổi dấu số nguyên nhỏ nhất vẫn ra chính nó do không thể biểu diễn đối số dương tương ứng trong 32 bit.

Nếu nghiệp vụ đòi **phát hiện overflow**, dùng các API như `Math.addExact`, `Math.subtractExact`, `Math.multiplyExact`, `Math.negateExact` hoặc thiết kế kiểu số lớn. Trong bytecode, chúng thường là **lời gọi phương thức** (ví dụ `invokestatic`), không phải một biến thể “`iaddExact`” của JVMS.

### Chia nguyên và phép dư

- `idiv`/`ldiv` chia số nguyên có dấu; phần thương được làm tròn **về 0**.
- `irem`/`lrem` là phần dư cùng quy tắc chia; khi phần dư khác 0, **dấu của phần dư theo số bị chia**.
- Nếu **số chia bằng 0**, `idiv`, `ldiv`, `irem`, `lrem` ném `ArithmeticException`.
- Trường hợp đặc biệt `Integer.MIN_VALUE / -1` trả `Integer.MIN_VALUE` do overflow; `Integer.MIN_VALUE % -1` trả `0`. Bản `long` tương tự.

~~~java
System.out.println(-7 / 3); // -2
System.out.println(-7 % 3); // -1
System.out.println(7 % -3); // 1
~~~

Nếu muốn phép chia hướng về âm vô cực hoặc modulo toán học dùng trong một số thuật toán, xem `Math.floorDiv` và `Math.floorMod`, vì dấu của kết quả có thể khác với toán tử `/` và `%`.

### Số thực khác số nguyên

Với `float`/`double`, JVM thực thi theo semantics dấu phẩy động IEEE 754 được Java quy định:

- `1.0 / 0.0` cho `+Infinity`; `-1.0 / 0.0` cho `-Infinity`.
- `0.0 / 0.0` cho `NaN`, không ném `ArithmeticException` chỉ vì mẫu số bằng 0.
- Phép toán vượt miền hữu hạn có thể sinh vô cực; phép toán quá nhỏ có thể sinh subnormal rồi tiến về `±0.0`.
- `frem`/`drem` làm phép dư dấu phẩy động theo **quy tắc remainder của Java, không phải định nghĩa IEEE remainder lấy thương nguyên gần nhất**. Khi muốn phép IEEE remainder, dùng `Math.IEEEremainder`.
- `fneg`/`dneg` đảo dấu giá trị dấu phẩy động; với `+0.0` và `-0.0` bit dấu là khác nhau.

Từ **Java 17**, JEP 306 khôi phục **always-strict floating-point semantics**: các phép dấu phẩy động của Java có quy tắc strict, không còn khác biệt ngữ nghĩa do lựa chọn tính intermediate exponent mở rộng như trước. Dù vậy, cộng số thực vẫn có làm tròn và không nhất thiết có tính kết hợp; thứ tự phép tính khác có thể tạo kết quả cuối khác.

## 3. Lệnh tăng biến cục bộ `iinc`

`iinc` là opcode đặc biệt: nó **tăng một biến cục bộ kiểu `int` bằng một hằng số có dấu mã hóa trong instruction**, thay vì pop hai toán hạng từ operand stack.

~~~text
iinc <local_index>, <signed_constant>
~~~

Ví dụ compiler có thể dùng `iinc 1, 1` cho `i++` hoặc `++i` khi `i` là một biến local kiểu `int` và kết quả biểu thức không cần giữ trên stack. Hành vi tăng cũng tuân theo overflow 32-bit như phép cộng nguyên thông thường.

- `iinc` không dùng để tăng trực tiếp local `long` hoặc field của đối tượng.
- Compiler không bị buộc phải sinh `iinc` cho mọi cú pháp `i++`. Nếu cần giá trị trước/sau increment, compiler có thể dùng thêm các lệnh load/store hoặc chọn chuỗi khác.
- `iinc` dạng thường chứa operand local index và hằng 8-bit có dấu; tiền tố `wide` cho phép các operand lớn hơn.

Đừng gọi `iinc` là instruction tính trên operand stack: nó tác động **trực tiếp vào một local variable** trong frame hiện hành.

## 4. Nhóm phép bit: AND, OR, XOR

JVM hỗ trợ các phép bit trên **`int` và `long`**:

| Toán tử nguồn | Opcode trên `int` | Opcode trên `long` | Ý nghĩa từng bit |
| --- | --- | --- | --- |
| `&` | `iand` | `land` | Bit kết quả là 1 khi cả hai bit tương ứng đều 1. |
| `|` | `ior` | `lor` | Bit kết quả là 1 khi ít nhất một bit bằng 1. |
| `^` | `ixor` | `lxor` | Bit kết quả là 1 khi hai bit tương ứng khác nhau. |

Ví dụ số nhỏ 4 bit:

~~~text
a = 10  = 1010
b = 12  = 1100

a & b  = 1000 = 8
a | b  = 1110 = 14
a ^ b  = 0110 = 6
~~~

Đây là minh họa ở bốn bit thấp, không phải giới hạn kích thước `int`. Với `int` thực tế mọi phép đều thao tác đủ 32 bit; với `long` là 64 bit.

**Không có `fand`, `dand` hay opcode `inot`.** Toán tử nguồn `~x` đảo từng bit trên `int`/`long`, có thể được compiler triển khai bằng XOR với mẫu bit toàn 1 (`-1`), ví dụ `iconst_m1` rồi `ixor`. Không có instruction JVM riêng mang tên `inot` cho phép đảo bit nguyên.

### Bitwise với boolean khác short-circuit

Trong ngôn ngữ Java, `&`, `|` và `^` cũng có thể áp dụng cho `boolean`, nhưng biểu thức boolean đó thường được compiler chuyển thành các thao tác int/branch thích hợp. Phân biệt:

- `a && b` và `a || b`: **short-circuit**, vế phải có thể không được đánh giá.
- `a & b` và `a | b` với boolean: **luôn đánh giá cả hai vế**.
- `^` boolean: true khi hai toán hạng khác nhau.

Hệ thống bytecode không có tập opcode arithmetic riêng cho boolean; chi tiết compiler có thể dùng các lệnh int, nhánh hoặc tổ hợp tùy ngữ cảnh.

### Kiểm tra bit flag

~~~java
int permissions = 0b1010;
int writeFlag = 0b0010;

boolean canWrite = (permissions & writeFlag) != 0;
System.out.println(canWrite); // true
~~~

Đây là đoạn minh họa bên trong một phương thức. Với bit mask, hãy chú ý signedness khi dịch bit và khi serialize sang kiểu có độ rộng khác.

## 5. Nhóm phép dịch: `shl`, `shr` và `ushr`

Chỉ `int` và `long` hỗ trợ dịch bit trực tiếp; shift count trên operand stack là **`int`** trong cả hai trường hợp:

| Java | `int` | `long` | Mô tả |
| --- | --- | --- | --- |
| `value << distance` | `ishl` | `lshl` | Dịch trái, lấp bit thấp bằng 0; bỏ bit cao tràn khỏi độ rộng. |
| `value >> distance` | `ishr` | `lshr` | Dịch phải **số học**, lấp bit cao bằng bit dấu cũ. |
| `value >>> distance` | `iushr` | `lushr` | Dịch phải **logic**, lấp bit cao bằng 0. |

Với toán hạng âm, `>>` và `>>>` khác nhau rõ rệt:

~~~java
int x = -8;
System.out.println(x >> 1);  // -4
System.out.println(x >>> 1); // 2147483644
~~~

Trong biểu diễn bù hai, `-8` là một chuỗi 32 bit bắt đầu bằng nhiều bit 1. Dịch phải số học giữ bit dấu 1 nên vẫn âm; dịch phải logic lấp bằng 0 nên trở thành một giá trị dương lớn.

### Shift count được mask

JVM sử dụng **5 bit thấp nhất** của khoảng dịch cho `int`, tức khoảng cách thực tế là `distance & 0x1F` (modulo 32). Với `long`, JVM sử dụng **6 bit thấp nhất**, tức `distance & 0x3F` (modulo 64).

~~~java
System.out.println(1 << 32);  // 1, vì 32 & 31 = 0
System.out.println(1L << 64); // 1, vì 64 & 63 = 0
System.out.println(1 << -1);  // -2147483648, vì -1 & 31 = 31
~~~

Khoảng dịch âm **không tự gây exception**; quy tắc bit mask vẫn áp dụng. Đây là nguồn lỗi phổ biến nếu giả định rằng dịch nhiều hơn độ rộng bit luôn trả về 0.

Chú ý: dịch trái tràn bit không tự gây exception; `x << n` chỉ tương đương nhân với lũy thừa của 2 trong miền không overflow và với các điều kiện giá trị thích hợp. Khi dùng shift để tính kích thước buffer, bit mask hoặc mã hóa, nên xác định rõ độ rộng dữ liệu và xử lý overflow.

## 6. Nhóm so sánh: instruction tạo kết quả và instruction rẽ nhánh

Ở bytecode có **hai cơ chế thường bị nhập làm một**:

1. Lệnh so sánh các giá trị lớn/float và **push một `int` kết quả** lên stack.
2. Lệnh nhánh điều kiện (**`if...`**) kiểm tra một hoặc hai giá trị trên stack và quyết định chuyển điểm thực thi đến offset khác.

Ví dụ mã Java §a < b§ không nhất thiết phải sinh một instruction tên §icmp§ rồi trả boolean; với hai §int§, JVM có instruction nhánh trực tiếp §if_icmplt§.

### So sánh hai `int` rồi rẽ nhánh

| Opcode | Nhánh khi |
| --- | --- |
| `if_icmpeq` | value1 == value2 |
| `if_icmpne` | value1 != value2 |
| `if_icmplt` | value1 < value2 |
| `if_icmple` | value1 <= value2 |
| `if_icmpgt` | value1 > value2 |
| `if_icmpge` | value1 >= value2 |

Những lệnh này **pop hai giá trị int**; nếu điều kiện đúng, control flow nhảy đến nhãn/offset đích, không tự push một giá trị Java §boolean§ 1 hoặc 0. Để trả boolean từ một biểu thức so sánh, compiler có thể tạo code nhánh và đưa §iconst_0§/§iconst_1§ lên stack, sau đó dùng §ireturn§.

Nhóm `ifeq`, `ifne`, `iflt`, `ifle`, `ifgt`, `ifge` lại kiểm tra **một giá trị int đối chiếu với 0**. Chúng thường dùng sau `lcmp`, `fcmp*`, `dcmp*` hoặc cho điều kiện đã mã hóa thành int.

Ngoài so sánh số, có `if_acmpeq`/`if_acmpne` để so sánh **hai tham chiếu đối tượng** theo identity và `ifnull`/`ifnonnull` để kiểm tra null; những instruction này không thực hiện `Object.equals`.

### So sánh hai `long`: `lcmp`

`lcmp` lấy hai giá trị `long` trên stack, thực hiện so sánh **có dấu**, rồi push kết quả kiểu `int`:

~~~text
value1 > value2  →  1
value1 = value2  →  0
value1 < value2  → -1
~~~

Ví dụ phương thức minh họa:

~~~java
static boolean isEarlier(long a, long b) {
    return a < b;
}
~~~

Một compiler Java thông thường có thể dùng `lcmp` rồi `ifge` (hoặc dạng nhánh tương đương) để xây dựng boolean. **Không có chuỗi sáu opcode `if_lcmpeq`...** tương ứng với `if_icmp*` của int.

### So sánh `float`/`double` và NaN

Các opcode:

- `fcmpl`, `fcmpg` cho `float`.
- `dcmpl`, `dcmpg` cho `double`.

Cả bốn lệnh đều lấy hai giá trị số thực và push một `int`:

| Điều kiện so sánh | `fcmpl/dcmpl` | `fcmpg/dcmpg` |
| --- | --- | --- |
| value1 < value2 | -1 | -1 |
| value1 == value2 | 0 | 0 |
| value1 > value2 | 1 | 1 |
| Ít nhất một operand là **NaN** | **-1** | **1** |

Sự khác nhau giữa hậu tố `l` (*less*) và `g` (*greater*) chỉ xuất hiện khi có **NaN**. Điều này quan trọng vì toán tử so sánh số học của Java yêu cầu mọi điều kiện `<`, `<=`, `>`, `>=` đều false nếu một operand là NaN.

Ví dụ compiler muốn triển khai **`a < b`** thường chọn `dcmpg` rồi dùng `ifge`: nếu gặp NaN, `dcmpg` trả 1 và §ifge§ sẽ chuyển sang nhánh false. Với **`a > b`**, có thể dùng `dcmpl` rồi `ifle` để NaN đi về nhánh false. Compiler có thể chọn chuỗi khác nhưng phải bảo toàn semantics.

**Hai số không dấu khác nhau:** `+0.0 == -0.0` là true trong so sánh số học; các opcode `fcmp*`/`dcmp*` coi hai dạng số không là bằng nhau. Nhưng `Double.compare(-0.0, +0.0) < 0` là true, do phương thức thư viện áp dụng **thứ tự toàn phần** khác ngữ nghĩa toán tử/JVM compare instruction. Không dùng `Double.compare` để đoán trực tiếp kết quả của `dcmpl` hoặc `dcmpg` khi có NaN hoặc signed zero.

### Vì sao không có opcode trả kết quả “boolean trên stack”?

Ở mức JVM, kết quả điều kiện thường được biểu diễn bằng đường điều khiển và các hằng int. Một kiểu Java §boolean§ trong biểu thức nguồn có thể tương ứng với giá trị int 0/1 trong nhiều thao tác bytecode. Đây là lý do không nên suy diễn “§if_icmplt§ push true/false” — instruction này chỉ **rẽ nhánh**.

## 7. Nhóm chuyển kiểu số: mã lệnh `x2y`

Tên opcode `i2l` có nghĩa **chuyển int thành long**, `d2i` là **double thành int**. Tập instruction chuyển đổi được thiết kế cho kiểu JVM, nên khác cách viết cast đầy đủ trong mã Java.

### Mở rộng (widening) kiểu nguyên thủy

| Opcode | Chuyển đổi | Ghi chú |
| --- | --- | --- |
| `i2l` | int → long | Bảo toàn chính xác mọi int. |
| `i2f` | int → float | Có thể làm tròn, mất độ chính xác vì chỉ 24 bit significand. |
| `i2d` | int → double | Bảo toàn chính xác mọi int 32-bit. |
| `l2f` | long → float | Có thể làm tròn nhiều bit thấp. |
| `l2d` | long → double | Có thể mất độ chính xác với long lớn. |
| `f2d` | float → double | Bảo toàn chính xác giá trị số hữu hạn của float trong double (không biến số thập phân ban đầu thành “chính xác”). |

Không có opcode `b2i`, `s2i`, `c2i` trong JVM cho việc đổi `byte`/`short`/`char` sang int: các kiểu đó thường được JVM biểu diễn dưới dạng int trong các thao tác liên quan.

**Widening không đồng nghĩa không mất dữ liệu ở mọi trường hợp.** Ví dụ một `long` lớn hơn 2^53 có thể mất bit thấp khi chuyển thành `double`; một `int` không nhất thiết được biểu diễn chính xác khi chuyển thành `float`.

### Thu hẹp (narrowing)

| Opcode | Chuyển đổi | Điểm cần nhớ |
| --- | --- | --- |
| `i2b` | int → byte | Giữ 8 bit thấp rồi mở rộng dấu thành int trên stack. |
| `i2c` | int → char | Giữ 16 bit thấp, không mang dấu rồi biểu diễn dạng int trên stack. |
| `i2s` | int → short | Giữ 16 bit thấp rồi mở rộng dấu thành int trên stack. |
| `l2i` | long → int | Giữ 32 bit thấp, có thể đổi dấu. |
| `f2i`, `f2l` | float → int/long | Bỏ phần lẻ theo hướng 0; xử lý NaN/vô cực theo quy tắc riêng. |
| `d2i`, `d2l` | double → int/long | Tương tự với double; có thể làm tròn/cắt thông tin. |
| `d2f` | double → float | Làm tròn về binary32; có thể thành vô cực, số không hoặc subnormal. |

Ví dụ ép kiểu nguyên:

~~~java
System.out.println((byte) 130);  // -126
System.out.println((short) 65535); // -1
System.out.println((char) 65535 == '\uFFFF'); // true
System.out.println((int) 3.9); // 3
System.out.println((int) -3.9); // -3
~~~

Với `i2b`, 130 ở nhị phân giữ 8 bit thấp là `10000010`, diễn giải có dấu thành -126. Với `i2c`, 16 bit được xem không dấu trong miền mã unit `char` 0..65535. Không có “exception tràn kiểu” tự động khi cast primitive theo các quy tắc này.

### Float/double sang int/long: NaN, vô cực và giới hạn

Đối với các opcode như `d2i`:

1. Nếu đầu vào là **NaN**, kết quả là `0`.
2. Nếu là số hữu hạn trong miền sau khi bỏ phần lẻ hướng 0, kết quả là số nguyên đó.
3. Nếu giá trị vượt miền int dương hoặc là `+Infinity`, kết quả là `Integer.MAX_VALUE`.
4. Nếu thấp hơn miền int âm hoặc là `-Infinity`, kết quả là `Integer.MIN_VALUE`.

Đối với `d2l`/`f2l`, áp dụng tương tự với miền `long` và `Long.MAX_VALUE/MIN_VALUE`.

~~~java
System.out.println((int) Double.NaN);               // 0
System.out.println((int) Double.POSITIVE_INFINITY); // 2147483647
System.out.println((int) Double.NEGATIVE_INFINITY); // -2147483648
System.out.println((int) 3.99);                     // 3
System.out.println((int) -3.99);                    // -3
~~~

Đừng cho rằng ép kiểu số thực sang số nguyên sẽ **ném ngoại lệ** khi đầu vào NaN hoặc vô cực. Nếu nghiệp vụ cần từ chối những giá trị này, hãy kiểm tra `Double.isFinite(x)` hoặc `Float.isFinite(x)` trước cast, và chủ động kiểm tra miền giá trị hợp lệ.

### Phân biệt ép primitive và cast tham chiếu

- `i2d`, `d2i` và tương tự: **chuyển giá trị primitive**.
- `checkcast`: **kiểm tra/ép kiểu tham chiếu** và có thể ném `ClassCastException`.
- `instanceof`: kiểm tra quan hệ kiểu runtime của tham chiếu.

Ba việc này có tên “cast” trong cách diễn đạt thông thường nhưng sử dụng cơ chế bytecode khác nhau. `int` sang `Integer` còn có thể biên dịch thành **boxing** qua lời gọi thư viện như `Integer.valueOf`, không phải `checkcast`.

## 8. Chương trình Java 17+ chạy được: đối chiếu từng nhóm

Tạo tệp **`BytecodeOpsDemo.java`** có tên trùng `public class`:

~~~java
public class BytecodeOpsDemo {
    static int add(int a, int b) {
        return a + b;
    }

    static int bits(int a, int b) {
        return (a & b) ^ (a << 1);
    }

    static int unsignedShift(int value) {
        return value >>> 1;
    }

    static boolean lessThanLong(long a, long b) {
        return a < b;
    }

    static boolean lessThanDouble(double a, double b) {
        return a < b;
    }

    static int toInt(double value) {
        return (int) value;
    }

    static byte toByte(int value) {
        return (byte) value;
    }

    public static void main(String[] args) {
        System.out.println("add=" + add(7, 5));
        System.out.println("overflow=" + (Integer.MAX_VALUE + 1));
        System.out.println("remainder=" + (-7 % 3));
        System.out.println("and=" + (10 & 12));
        System.out.println("or=" + (10 | 12));
        System.out.println("xor=" + (10 ^ 12));
        System.out.println("not=" + (~10));
        System.out.println("bitMethod=" + bits(10, 12));
        System.out.println("signedShift=" + (-8 >> 1));
        System.out.println("unsignedShift=" + unsignedShift(-8));
        System.out.println("longLess=" + lessThanLong(4L, 9L));
        System.out.println("nanLess=" + lessThanDouble(Double.NaN, 1.0));
        System.out.println("cast=" + toInt(3.9));
        System.out.println("nanCast=" + toInt(Double.NaN));
        System.out.println("infinityCast=" + toInt(Double.POSITIVE_INFINITY));
        System.out.println("byteCast=" + toByte(130));
        System.out.println("floatDivide=" + (1.0 / 0.0));
    }
}
~~~

Biên dịch và chạy với **JDK 17+**:

~~~bash
javac --release 17 BytecodeOpsDemo.java
java BytecodeOpsDemo
~~~

**Stdout chính xác:**

~~~text
add=12
overflow=-2147483648
remainder=-1
and=8
or=14
xor=6
not=-11
bitMethod=28
signedShift=-4
unsignedShift=2147483644
longLess=true
nanLess=false
cast=3
nanCast=0
infinityCast=2147483647
byteCast=-126
floatDivide=Infinity
~~~

Kiểm tra kỹ `bitMethod=28`: `(10 & 12) = 8`, còn `(10 << 1) = 20`; `8 ^ 20 = 28`. Không cộng số học hai kết quả này; `^` là XOR từng bit.

### Xem bytecode bằng javap

~~~bash
javap -c -p BytecodeOpsDemo
~~~

Bạn có thể tìm các dấu hiệu trong phương thức đã tách riêng:

| Phương thức | Instruction chính dự kiến khi biên dịch bằng `javac` thông thường |
| --- | --- |
| `add` | `iload_0`, `iload_1`, `iadd`, `ireturn` |
| `bits` | `iand`, `ishl`, `ixor` |
| `unsignedShift` | `iushr` |
| `lessThanLong` | `lcmp` và một opcode `if...` |
| `lessThanDouble` | `dcmpg` và một opcode nhánh để bảo đảm NaN cho false |
| `toInt` | `d2i`, `ireturn` |
| `toByte` | `i2b`, `ireturn` |

Đây là **kỳ vọng theo compiler Java thông thường**, không phải bản dump exact có số offset/chỉ mục constant pool bắt buộc. Compiler được phép sinh những chuỗi bytecode khác nhưng phải giữ ngữ nghĩa Java. Trong `main`, thao tác nối chuỗi khi in có thể liên quan §invokedynamic§ hoặc các lời gọi thư viện; không nên nhầm những lệnh tạo chuỗi ấy với opcode số học đang khảo sát.

### Minh họa quá trình stack cho `add(7, 5)`

~~~text
Local variables: [0]=7, [1]=5
Stack ban đầu: []

iload_0  → Stack [7]
iload_1  → Stack [7, 5]
iadd     → Stack [12]
ireturn  → Trả 12 về caller
~~~

Với `lessThanLong(4L, 9L)`, `lcmp` có thể đẩy kết quả int `-1` rồi nhánh điều kiện biến nó thành boolean đúng. Với `lessThanDouble(Double.NaN, 1.0)`, `dcmpg` có thể đẩy `1`, vì vậy điều kiện `<` được coi false như yêu cầu Java.

## 9. Những khác biệt quan trọng giữa toán tử Java và opcode JVM

### Compiler có thể gấp hằng (constant folding)

Đối với biểu thức thuần hằng như `2 + 3` hoặc `10 & 12`, compiler có thể tính sẵn giá trị và phát instruction nạp hằng, thay vì tạo `iadd` hoặc `iand`. Muốn **quan sát opcode** cụ thể, nên dùng phương thức nhận tham số động như `add(int,int)`, `bits(int,int)` trong ví dụ.

### Chuỗi cộng không phải phép cộng số

Trong mã nguồn, `"total=" + value` là **string concatenation**, không phải `iadd` hay `dadd`. Với JDK 17, `javac` thường dùng cơ chế `invokedynamic` phục vụ ghép chuỗi. Đừng tìm một opcode kiểu `sadd`: JVM không có lệnh cộng chuỗi primitive riêng.

### Numeric promotion trước khi thực hiện phép toán

Cú pháp:

~~~java
byte a = 100;
byte b = 20;
// byte result = a + b; // Không biên dịch: a + b có kiểu int.
int result = a + b;
~~~

Dù hai toán hạng là §byte§, Java thực hiện **binary numeric promotion** và biểu thức có kiểu §int§. JVM thường sử dụng `iadd`. Muốn lưu trở lại §byte§, cần cast rõ hoặc compound assignment theo ngữ nghĩa Java:

~~~java
byte resultByte = (byte) (a + b); // 120
~~~

Đây là đoạn minh họa phương thức; cast còn có thể phát `i2b`. Việc dùng `byte`/`short`/`char` không làm bytecode chuyển sang một bộ instruction arithmetic 8/16-bit riêng.

### Phương thức thư viện không phải opcode chuyên dụng

`Math.abs`, `Math.addExact`, `Integer.compareUnsigned`, `BigDecimal.add`, `Double.compare` không phải tên instruction trong JVMS. Compiler có thể phát lời gọi phương thức; JVM/JIT có thể tối ưu hoặc sử dụng intrinsic trong runtime cụ thể, nhưng **định dạng bytecode của phương thức gọi** vẫn tuân thủ instruction set JVM chứ không mặc nhiên có opcode cùng tên API.

### Tính tràn của `int` khác `float` và `double`

- Với `iadd`, kết quả là giá trị bit-width cố định modulo 2^32 (diễn giải có dấu), không tự sinh Infinity hoặc NaN.
- Với `fadd`/`dadd`, kết quả có thể được làm tròn, tràn về vô cực hoặc mang NaN.
- Với `idiv`, số chia 0 ném `ArithmeticException`; với `ddiv`, `1.0 / 0.0` là Infinity, còn `0.0 / 0.0` là NaN.

Không thể dùng một quy tắc “phép toán / luôn ném khi chia cho 0” cho cả hai họ kiểu.

## 10. Lỗi thường gặp và cách điều tra

| Triệu chứng | Nguyên nhân | Hướng điều tra/xử lý |
| --- | --- | --- |
| `int` cộng số lớn biến thành âm | Overflow 32-bit không có exception | Dùng `Math.addExact` khi cần phát hiện; `long`/`BigInteger` khi cần miền lớn. |
| `Integer.MIN_VALUE / -1` không thành số dương | Thương toán học không biểu diễn được trong int | Kiểm tra riêng trường hợp biên, chọn kiểu lớn hơn nếu cần. |
| §byte + byte§ không gán trực tiếp lại §byte§ | Binary numeric promotion thành int | Dùng biến int hoặc cast có kiểm tra miền. |
| `-7 % 3` ra `-1` | Java remainder có dấu theo số bị chia | Dùng `Math.floorMod` nếu cần modulo theo quy ước khác. |
| `>>>` trên số âm cho kết quả dương lớn | Bit cao lấp 0, không giữ sign bit | Phân biệt `ishr` với `iushr` và độ rộng 32/64. |
| `1 << 32` ra 1 | Shift count int chỉ dùng 5 bit thấp | Mask khoảng dịch và validate số bit mong đợi. |
| So sánh NaN bằng `<` hoặc `>` đều false | NaN unordered; compiler dùng `fcmpg/fcmpl` hoặc `dcmpg/dcmpl` đúng ngữ nghĩa | Kiểm tra `Double.isNaN`/`Double.isFinite` khi cần. |
| Cast `Double.NaN` thành `int` ra 0 | Quy tắc narrowing floating-point → integer | Validate dữ liệu trước ép kiểu. |
| Cast vô cực thành `int` không ném exception | Narrowing bị giới hạn tại biên miền int/long | Kiểm tra hữu hạn và khoảng giá trị. |
| Không thấy `iadd` trong `2 + 3` | Compiler đã constant fold | Quan sát phương thức nhận tham số động. |
| `javap -c` không giống “mỗi toán tử một opcode” | Nạp/lưu stack, branch, calls và tối ưu | Đọc toàn bộ stack flow, không so từng ký hiệu máy móc. |
| Bytecode lỗi type/stack không chạy | Không thỏa điều kiện verifier | Xem `VerifyError`, định dạng class và stack map; không tự phát bytecode sai. |

### Cách đọc §javap§ có hệ thống

1. Xác định chữ ký phương thức (tham số, kiểu trả về), chú ý một tham số `long`/`double` dùng hai slot trong local variable array theo quy tắc JVM.
2. Theo dõi local variables được nạp bằng `iload`, `lload`, `fload`, `dload`.
3. Vẽ operand stack sau mỗi instruction. Với lệnh binary, operand ở đỉnh là giá trị thứ hai.
4. Nhận diện opcode tính toán, chuyển kiểu, so sánh và nhánh; tách chúng khỏi lời gọi `invoke*`.
5. Quan sát đường đi khi nhánh đúng/sai; bảo đảm stack shape hợp lệ tại điểm hội tụ.
6. Với thông tin phụ, dùng `javap -v -p TênLớp` để xem thêm constant pool, descriptor, StackMapTable và các thuộc tính khác.

**Đừng cố suy ra toàn bộ source Java ban đầu chỉ từ vài instruction**: nhiều cách viết nguồn có thể biên dịch thành bytecode tương đương, đặc biệt khi có constant folding hoặc compiler tối ưu.

## 11. Tự kiểm tra kiến thức

**Câu 1.** `iadd` lấy hai số từ đâu? `iinc` có giống không?

**Đáp án:** `iadd` pop hai giá trị int từ operand stack rồi push tổng. `iinc` tăng một local variable kiểu int bằng hằng đã mã hóa, không lấy hai toán hạng từ stack.

**Câu 2.** `idiv` và `ddiv` khác nhau thế nào khi số chia bằng 0?

**Đáp án:** `idiv` ném `ArithmeticException`, còn `ddiv` tuân thủ IEEE 754: tùy tử số và dấu, có thể ra vô cực hoặc NaN.

**Câu 3.** Hai toán hạng là `byte`, tại sao thường thấy `iadd`?

**Đáp án:** Java binary numeric promotion biến biểu thức cộng thành int; JVM không có opcode `badd` riêng.

**Câu 4.** Với `int x = -8`, `x >> 1` và `x >>> 1` khác gì?

**Đáp án:** `>>` giữ dấu nên ra -4; `>>>` đưa 0 vào bit cao nên ra 2147483644.

**Câu 5.** `1 << 32` bằng bao nhiêu và vì sao?

**Đáp án:** Bằng 1, vì shift count với int chỉ sử dụng 5 bit thấp: 32 trở thành khoảng dịch 0.

**Câu 6.** `lcmp` trả gì khi value1 nhỏ hơn value2? Nó trực tiếp trả `boolean` chứ?

**Đáp án:** Push số nguyên -1 lên stack, không trực tiếp trả một boolean Java. Compiler dùng thêm nhánh để xây dựng kết quả boolean nếu cần.

**Câu 7.** Vì sao `dcmpg` và `dcmpl` khác nhau khi gặp NaN?

**Đáp án:** NaN unordered; `dcmpg` chọn kết quả int +1 và `dcmpl` chọn -1. Hai loại cho phép compiler tạo nhánh đúng ngữ nghĩa mọi phép so sánh của Java.

**Câu 8.** `(int) Double.NaN`, `(int) Double.POSITIVE_INFINITY` và `(int) -3.9` cho kết quả gì?

**Đáp án:** Lần lượt 0, 2147483647 và -3. Trường hợp NaN và Infinity do quy tắc narrowing đặc biệt, số hữu hạn bị bỏ phần lẻ hướng 0.

**Câu 9.** `i2d` có mất chính xác với bất kỳ `int` nào không? `l2d` thì sao?

**Đáp án:** `i2d` bảo toàn chính xác mọi giá trị int 32-bit; `l2d` có thể mất bit thấp của long quá lớn vì double chỉ có 53 bit phần trị hiệu dụng.

**Câu 10.** Tại sao có khi mã Java `2 + 3` không có `iadd` trong disassembly?

**Đáp án:** Compiler có thể tính hằng tại compile time thành 5 và sinh instruction nạp 5; không phải JVM bỏ phép cộng sai.

## 12. Sơ đồ ghi nhớ

~~~text
Toán tử Java                     Opcode JVM điển hình
--------------------------------------------------------
int a+b, a-b, a*b                iadd / isub / imul
long a/b, a%b                    ldiv / lrem
float/double arithmetic          fadd..frem / dadd..drem
int/long bitwise &, |, ^         iand/ior/ixor, land/lor/lxor
int/long <<, >>, >>>             ishl/ishr/iushr, lshl/lshr/lushr
int local++                      có thể dùng iinc
int a < b                        thường if_icmplt
long a < b                       lcmp rồi branch
float/double a < b               fcmpg/dcmpg rồi branch
(int) someDouble                 d2i
(byte) someInt                   i2b

Operand stack: ... value1, value2 → ... result
Instruction branch: pop operand, thay đổi control flow, không push boolean
~~~

**Điểm cốt lõi:** học bytecode theo **kiểu operand, quy tắc stack và semantics của instruction**, không theo suy đoán “mỗi toán tử Java bằng đúng một lệnh”. Với số nguyên, quan sát độ rộng bit, overflow và dịch bit; với số thực, quan sát NaN/vô cực và làm tròn; với so sánh, phân biệt opcode tạo kết quả với nhánh; với chuyển kiểu, xác định chỗ mất dữ liệu.

## Tài liệu chuẩn

- [JVMS Java SE 21 — §2.11 Instruction Set Summary](https://docs.oracle.com/javase/specs/jvms/se21/html/jvms-2.html#jvms-2.11).
- [JVMS Java SE 21 — §6.5 Instructions](https://docs.oracle.com/javase/specs/jvms/se21/html/jvms-6.html#jvms-6.5).
- [JLS Java SE 21 — §5 Conversions and Contexts](https://docs.oracle.com/javase/specs/jls/se21/html/jls-5.html).
- [JLS Java SE 21 — §15 Expressions](https://docs.oracle.com/javase/specs/jls/se21/html/jls-15.html).
- [JDK 21 — javap documentation](https://docs.oracle.com/en/java/javase/21/docs/specs/man/javap.html).
- [JEP 306 — Restore Always-Strict Floating-Point Semantics](https://openjdk.org/jeps/306).


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="bytecode-instructions-004">Nhóm lệnh pop, dup, swap và ràng buộc kích thước giá trị</a>

<details>
<summary>Xem chi tiết</summary>

# Nhóm lệnh `pop`, `dup`, `swap` và ràng buộc kích thước giá trị trong JVM

Java Virtual Machine (JVM) dùng **operand stack** trong mỗi stack frame để giữ các toán hạng và kết quả trung gian khi chạy bytecode. Các lệnh `pop`, `pop2`, `dup`, `dup_x1`, `dup_x2`, `dup2`, `dup2_x1`, `dup2_x2` và `swap` thay đổi **thứ tự hoặc số bản sao giá trị** trên operand stack. Chúng không trực tiếp cộng số, gọi phương thức hay cấp phát đối tượng.

Điều quan trọng nhất: các opcode này phải tôn trọng **computational type category**. Một giá trị `long` hoặc `double` thuộc **category 2**, còn `int`, `float` và reference thuộc **category 1**. Vì thế, `pop` không thể lấy một `long` trực tiếp; `swap` không hoán đổi một `double` với một `int`; và `dup2` có thể sao chép **một giá trị category 2 hoặc hai giá trị category 1**.

## 1. Operand stack thuộc về một lần gọi phương thức

Khi JVM thực thi một method, nó có frame riêng gồm **local variables**, **operand stack** và thông tin phụ trợ. Bytecode đọc local variables hoặc tạo hằng số rồi đẩy giá trị lên operand stack; một lệnh khác có thể lấy các giá trị trên cùng để tính toán, gọi method hoặc ghi field.

```text
Mô hình frame (không phải bố cục RAM vật lý)

Local variables              Operand stack
slot 0: int a = 2             TOP ┌───────────────┐
slot 1: int b = 3                 │      3        │ ← giá trị trên cùng
                                 ├───────────────┤
                                 │      2        │
                                 └───────────────┘
                                      bottom

iload_0   → đẩy 2
iload_1   → đẩy 3
iadd      → lấy hai int, đẩy 5
```

Các hình trong bài đọc stack theo thứ tự **từ trái qua phải = từ dưới lên trên**:

```text
..., value3, value2, value1
                        ↑
                       TOP
```

Dấu `...` tượng trưng cho phần stack sâu hơn không bị lệnh thay đổi. `value1` luôn là giá trị trên cùng trước khi thao tác. Những tên `value1`, `value2` biểu diễn **giá trị logic**, không phải tên biến Java hay byte vật lý.

**`pop` của operand stack khác `Stack.pop()` trong Java Collections.** Ở đây chúng ta học JVM instruction từ class file. Không có phương thức Java chuẩn `dup()` hay `swap()` để người lập trình thao tác trực tiếp lên operand stack trong mã nguồn Java. Compiler chọn bytecode dựa trên cấu trúc nguồn.

## 2. Category 1 và category 2: quy tắc quyết định lệnh hợp lệ

| Loại giá trị trên operand stack | Category | Độ rộng theo mô hình stack | Ví dụ |
| --- | --- | --- | --- |
| `int` | 1 | 1 đơn vị stack (slot/word trong mô hình) | `42`, nhiều phép toán với `byte`, `short`, `char`, `boolean` |
| `float` | 1 | 1 đơn vị | `2.5f` |
| Reference | 1 | 1 đơn vị | Tham chiếu `String`, object, array, kể cả `null` |
| `long` | 2 | 2 đơn vị | `42L` |
| `double` | 2 | 2 đơn vị | `2.5d` |
| `returnAddress` lịch sử | 1 | 1 đơn vị | Gắn với `jsr`/`ret`, không có kiểu khai báo Java tương ứng |

**Category là khái niệm của JVM instruction set**, không khẳng định reference của một JVM 64-bit nhất thiết chiếm 4 hay 8 byte trên RAM. Các kiểu nguyên thủy `byte`, `short`, `char` thường đi qua các phép tính theo dạng `int`; `boolean` không có tập lệnh tính toán số học độc lập. Cũng không thể kết luận object “nằm ở stack”: operand stack giữ **giá trị reference** đến object, không chứa nguyên object theo nghĩa sao chép tất cả field.

Phân biệt hai cách đếm:

- **Số giá trị logic**: `long` là **một giá trị**, hai `int` là **hai giá trị**.
- **Số đơn vị độ rộng**: một `long` chiếm **hai đơn vị**; hai `int` cũng tổng cộng **hai đơn vị**.

Đây là lý do `pop2` và `dup2` có hai kiểu sử dụng hợp lệ. Hậu tố `2` diễn tả **tổng độ rộng tính theo category**, không luôn là “hai object” hay “hai giá trị”.

## 3. `pop` và `pop2`: bỏ kết quả không sử dụng

### `pop` — bỏ đúng một giá trị category 1

```text
Trước: ..., value1
Sau:   ...

Điều kiện: value1 là category 1.
Opcode: pop (0x57)
```

Ví dụ hợp lệ: bỏ một `int`, `float` hoặc reference vừa có trên stack. **Không hợp lệ** nếu `value1` là `long` hoặc `double`, bởi chúng là category 2.

### `pop2` — bỏ tổng cộng hai đơn vị độ rộng

```text
Form 1: hai giá trị category 1
Trước: ..., value2(cat1), value1(cat1)
Sau:   ...

Form 2: một giá trị category 2
Trước: ..., value(cat2)
Sau:   ...

Opcode: pop2 (0x58)
```

`pop2` không được dùng để lấy “nửa trên” của `long` hay `double`. Category 2 luôn là **một giá trị không thể bị xé đôi** bởi nhóm lệnh này.

### Tình huống compiler dùng `pop` và `pop2`

Trong Java source, việc gọi một phương thức trả giá trị rồi **không dùng kết quả** là hợp lệ:

```java
intResult();    // bỏ giá trị int trả về
longResult();   // bỏ giá trị long trả về
```

Đây là các dòng **minh họa đặt trong một method có định nghĩa `intResult()` và `longResult()` phù hợp**, chưa phải chương trình chạy độc lập. Với bytecode do `javac` thông thường tạo, lệnh gọi trả `int` được theo sau bởi `pop`, còn lệnh gọi trả `long` được theo sau bởi `pop2`. Trường hợp method trả `void` không có giá trị trả về để bỏ bằng `pop`.

```text
invokestatic ... intResult:()I
pop
invokestatic ... longResult:()J
pop2
```

Dấu `...` thay cho chỉ mục/names trong constant pool, đây là **minh họa bytecode**, không phải một listing phải giống hệt mọi compiler.

## 4. `dup` và `dup2`: sao chép giá trị trên stack

### `dup` — nhân đôi đúng một category 1

```text
Trước: ..., value1(cat1)
Sau:   ..., value1, value1

Opcode: dup (0x59)
```

`dup` không dùng trực tiếp được với `long` hay `double`. Nếu `value1` là **reference**, hai vị trí stack sau `dup` chứa hai **bản sao giá trị tham chiếu tới cùng object**, không tạo object thứ hai. Nếu `value1` là `int`, hai vị trí giữ cùng giá trị số tại thời điểm thao tác.

Một mẫu compiler phổ biến khi xử lý biểu thức `new Box()`:

```text
new Box
dup
invokespecial Box.<init>:()V
```

Đây là bytecode **minh họa**. `new` đẩy một reference tới object đang trong trạng thái chưa khởi tạo đầy đủ; `dup` giữ thêm bản sao để sau `invokespecial` gọi constructor, biểu thức vẫn có reference kết quả để sử dụng. Verifier còn quản lý trạng thái reference chưa khởi tạo và các ràng buộc constructor. Không nên xem chuỗi trên là cách tùy ý dùng object chưa được khởi tạo.

### `dup2` — sao chép hai category 1 hoặc một category 2

```text
Form 1 (hai cat1):
Trước: ..., value2(cat1), value1(cat1)
Sau:   ..., value2, value1, value2, value1

Form 2 (một cat2):
Trước: ..., value(cat2)
Sau:   ..., value, value

Opcode: dup2 (0x5C)
```

Đối với hai category 1, `dup2` **giữ nguyên thứ tự** cặp giá trị được nhân đôi. Đối với một `long`, nó tạo hai bản sao **toàn bộ giá trị `long`**, không thành bốn giá trị độc lập.

`dup`/`dup2` thường xuất hiện khi compiler cần giữ giá trị của một biểu thức cho **hai mục đích**: vừa gán cho field/array element, vừa để biểu thức gán có kết quả; hoặc vừa làm toán hạng của lệnh tiếp theo, vừa cần giữ bản sao cho kết quả trả về. Không được kết luận source thực hiện deep copy object chỉ từ một opcode `dup`.

## 5. `swap`: đảo vị trí hai category 1

```text
Trước: ..., value2(cat1), value1(cat1)
Sau:   ..., value1, value2

Opcode: swap (0x5F)
```

**Cả hai giá trị phải là category 1.** Các trường hợp sau là không hợp lệ với `swap`:

```text
..., long(cat2), int(cat1)    → không được swap
..., int(cat1), double(cat2) → không được swap
..., long(cat2), long(cat2)  → không được swap
```

Một reference là category 1 nên có thể tham gia `swap` với một `int` nếu việc xếp lại còn đáp ứng yêu cầu kiểu của các lệnh kế tiếp. Tuy nhiên **hợp lệ riêng về category chưa đủ để toàn bộ phương thức hợp lệ**: verifier còn kiểm tra kiểu thực, control-flow và các phép dùng sau đó.

Khi cần sắp xếp lại stack có `long`/`double`, compiler có thể dùng tổ hợp lệnh tải/lưu local variables hoặc các dạng `dup*_x*` hợp lệ, tùy ngữ cảnh. Không có opcode `swap2` tiêu chuẩn trong tập lệnh JVM để đơn giản “đảo mọi cặp giá trị 64 bit”.

## 6. Vì sao có `dup_x1`, `dup_x2`, `dup2_x1`, `dup2_x2`?

`dup` hoặc `dup2` chỉ tạo bản sao **ngay trên cùng**. Với biểu thức gán phức tạp, compiler có thể cần **đặt bản sao ở dưới các toán hạng khác** đang có trên operand stack. Nhóm có hậu tố `_x1`/`_x2` phục vụ điều đó.

Quy ước đọc sơ đồ:

- **cat1**: giá trị category 1 (một đơn vị).
- **cat2**: giá trị category 2 (hai đơn vị).
- `value1`: top của operand stack trước lệnh.
- Dấu `→`: thay đổi stack; vế phải vẫn viết theo thứ tự **dưới → trên**.
- Các giá trị chưa có trên sơ đồ (phần `...`) không đổi.

### 6.1. `dup_x1`: một dạng hợp lệ

Yêu cầu **hai giá trị trên cùng đều là category 1**.

```text
..., value2(cat1), value1(cat1)
→
..., value1, value2, value1

Opcode: dup_x1 (0x5A)
```

Nó sao chép `value1` rồi **chèn bản sao xuống dưới `value2`**. Nếu `value2` là một `double`, không được dùng `dup_x1`.

### 6.2. `dup_x2`: hai dạng hợp lệ

```text
Form 1: ba cat1
..., value3(cat1), value2(cat1), value1(cat1)
→
..., value1, value3, value2, value1

Form 2: một cat2 ở dưới một cat1
..., value2(cat2), value1(cat1)
→
..., value1, value2, value1

Opcode: dup_x2 (0x5B)
```

Giá trị được nhân đôi luôn là **top `value1` category 1**. Hai đơn vị ở dưới nó có thể là **hai cat1** hoặc **một cat2**. Không được thay `value1` bằng `long` rồi cho rằng hậu tố `x2` sẽ tự hợp thức hóa.

### 6.3. `dup2_x1`: hai dạng hợp lệ

```text
Form 1: ba cat1
..., value3(cat1), value2(cat1), value1(cat1)
→
..., value2, value1, value3, value2, value1

Form 2: một cat1 dưới một cat2
..., value2(cat1), value1(cat2)
→
..., value1, value2, value1

Opcode: dup2_x1 (0x5D)
```

Ở form 1, sao chép **cặp** `value2, value1` (cả hai cat1). Ở form 2, sao chép **một giá trị cat2** `value1`. Chúng được chèn vào dưới `value3` hoặc `value2` tương ứng; không tráo thứ tự bên trong cặp được sao chép.

### 6.4. `dup2_x2`: bốn dạng hợp lệ

Đây là trường hợp dễ sai nhất; cần **đọc category của từng giá trị** trước khi vẽ stack.

```text
Form 1: bốn cat1
..., value4(cat1), value3(cat1), value2(cat1), value1(cat1)
→
..., value2, value1, value4, value3, value2, value1

Form 2: hai cat1 ở dưới một cat2
..., value3(cat1), value2(cat1), value1(cat2)
→
..., value1, value3, value2, value1

Form 3: một cat2 ở dưới hai cat1
..., value3(cat2), value2(cat1), value1(cat1)
→
..., value2, value1, value3, value2, value1

Form 4: hai cat2
..., value2(cat2), value1(cat2)
→
..., value1, value2, value1

Opcode: dup2_x2 (0x5E)
```

Tương tự `dup2`, phần được sao chép ở top có tổng độ rộng **hai đơn vị**: **hai cat1** hoặc **một cat2**. Phần nằm dưới cũng có tổng độ rộng **hai đơn vị**: hai cat1 hoặc một cat2. Bốn cách phối hợp hợp lệ sinh ra **bốn form** trên.

### Bảng tổng hợp opcode và điều kiện

| Instruction | Opcode (hex) | Phần top bắt buộc | Ý nghĩa |
| --- | --- | --- | --- |
| `pop` | `57` | 1 cat1 | Bỏ một giá trị |
| `pop2` | `58` | 2 cat1 **hoặc** 1 cat2 | Bỏ hai đơn vị |
| `dup` | `59` | 1 cat1 | Sao chép top |
| `dup_x1` | `5A` | 2 cat1 | Sao chép top xuống dưới một cat1 |
| `dup_x2` | `5B` | 3 cat1 **hoặc** cat2 + cat1 | Sao chép top xuống dưới hai đơn vị |
| `dup2` | `5C` | 2 cat1 **hoặc** 1 cat2 | Sao chép hai đơn vị top |
| `dup2_x1` | `5D` | 3 cat1 **hoặc** cat1 + cat2 | Sao chép hai đơn vị top, chèn dưới một cat1 |
| `dup2_x2` | `5E` | Bốn dạng ở mục 6.4 | Sao chép hai đơn vị top, chèn dưới hai đơn vị |
| `swap` | `5F` | 2 cat1 | Đổi thứ tự hai giá trị |

**Lưu ý:** biểu thức “hai đơn vị” là độ rộng theo mô hình JVM, không phải số phần tử của collection, số byte, hoặc thao tác lên hai biến Java. Các thứ tự trong bảng điều kiện được hiểu **từ dưới lên trên**, còn sơ đồ đầy đủ mới xác định chính xác `value1` nằm ở đâu.

## 7. Trường hợp hợp lệ và không hợp lệ: tự suy ra bằng category

Giả sử stack có các giá trị sau; các số chỉ để phân biệt giá trị, không phải bytecode opcode:

| Stack trước lệnh (dưới → trên) | Lệnh | Kết quả / lý do |
| --- | --- | --- |
| `..., int(7)` | `dup` | `..., 7, 7`, hợp lệ |
| `..., long(7L)` | `dup` | **Không hợp lệ**: top cat2 |
| `..., long(7L)` | `dup2` | `..., 7L, 7L`, hợp lệ |
| `..., int(1), int(2)` | `dup2` | `..., 1, 2, 1, 2`, hợp lệ |
| `..., double(2.0)` | `pop` | **Không hợp lệ**: top cat2 |
| `..., double(2.0)` | `pop2` | `...`, hợp lệ |
| `..., int(1), long(2L)` | `pop2` | `..., 1`, hợp lệ vì chỉ lấy top long |
| `..., long(1L), int(2)` | `pop2` | **Không hợp lệ**: không thể lấy top int và nửa long |
| `..., int(1), float(2f)` | `swap` | `..., 2f, 1`, hợp lệ về category |
| `..., int(1), long(2L)` | `swap` | **Không hợp lệ**: một toán hạng cat2 |
| `..., long(1L), int(2)` | `dup_x2` | `..., 2, 1L, 2`, form 2 |
| `..., int(1), long(2L)` | `dup2_x1` | `..., 2L, 1, 2L`, form 2 |

Điểm gây nhầm phổ biến ở `pop2`: nếu top là cat2, lệnh **chỉ bỏ một giá trị đó**, bất kể còn bao nhiêu giá trị cat1 ở dưới. Nếu top là cat1 nhưng ngay dưới là cat2, không có form hợp lệ cho `pop2` vì thiếu một cat1 thứ hai trên cùng.

Không thể xử lý giá trị cat2 như hai cat1 bằng cách gọi `pop` hai lần. Category 2 là đơn vị nguyên vẹn đối với các instruction này. Đặc tả JVM quy định những mẫu stack hợp lệ, và **bytecode verifier** dựa vào các mẫu kiểu trong quá trình kiểm tra.

## 8. Tại sao `javac` cần những lệnh này?

Ở Java source, một phép gán có thể đồng thời **tác động nơi lưu giá trị** và **trả về giá trị biểu thức**. Ví dụ `int y = (x = 5);`: phép gán `x = 5` phải để lại một giá trị để gán tiếp vào `y`. Compiler có thể giữ bản sao giá trị trên operand stack thay vì nạp lại từ local variables.

Với phép gán vào field hoặc array, thứ tự stack còn bao gồm **reference của object/array**, index và giá trị mới. Các biến thể `dup_x1`, `dup_x2`, `dup2_x1`, `dup2_x2` cho phép compiler giữ một bản sao của kết quả biểu thức **bên dưới** những toán hạng mà `putfield` hoặc `xastore` cần lấy.

### Ví dụ khái niệm: cập nhật field rồi vẫn giữ giá trị

Giả sử bên trong một phương thức hợp lệ có:

```java
Box box = new Box();
int result = (box.value = 9);
```

Trong một chiến lược biên dịch thông thường, trước `putfield` có `objectref` và `int value` trên stack. Nhưng biểu thức gán còn cần kết quả `9`, do đó compiler có thể dùng `dup_x1`:

```text
Trước: ..., objectref(cat1), value=9(cat1)
dup_x1:
       ..., 9, objectref, 9
putfield Box.value:I:
       ..., 9            // bản sao dùng làm kết quả biểu thức
```

`Box` và `value` ở đây là kiểu/field giả định cho đoạn minh họa; ví dụ chạy được đầy đủ nằm ở mục 9. Sơ đồ chỉ trình bày một **mẫu biên dịch hợp lý**, không bắt mọi compiler hay JIT giữ đúng các lệnh trung gian này.

### Ví dụ khái niệm: gán phần tử mảng

Để thực thi `result = (numbers[index] = 9)`, `iastore` cần ba toán hạng:

```text
..., arrayref(cat1), index(cat1), value=9(cat1)
```

Compiler có thể dùng `dup_x2` (form 1) nhằm giữ bản sao `9`:

```text
Trước:     ..., arrayref, index, 9
dup_x2:    ..., 9, arrayref, index, 9
iastore:   ..., 9
```

Nếu giá trị phần tử là `long` (cat2), chẳng hạn `long[]`, thì việc giữ bản sao của giá trị rộng và chèn dưới `arrayref`, `index` (hai cat1) có thể dùng **`dup2_x2` form 2**:

```text
Trước:      ..., arrayref(cat1), index(cat1), value(cat2)
dup2_x2:    ..., value(cat2), arrayref, index, value(cat2)
lastore:    ..., value(cat2)
```

Vậy sự khác biệt giữa `dup_x2` và `dup2_x2` xuất phát từ **category của giá trị phần tử**, chứ không phải kích thước mảng hay số dấu ngoặc trong source.

## 9. Thực hành Java 17+: sinh bytecode và đọc bằng `javap`

Tạo **một tệp** `StackInstructionDemo.java` với nội dung sau:

```java
public class StackInstructionDemo {
    static class Box {
        int value;
    }

    static int intResult() {
        return 7;
    }

    static long longResult() {
        return 9L;
    }

    static int assignedField(Box box) {
        int result = (box.value = 12);
        return result;
    }

    static int assignedElement(int[] numbers, int index) {
        int result = (numbers[index] = 21);
        return result;
    }

    static long assignedLongElement(long[] numbers, int index) {
        long result = (numbers[index] = 34L);
        return result;
    }

    public static void main(String[] args) {
        intResult();
        longResult();

        Box box = new Box();
        int[] numbers = new int[1];
        long[] longNumbers = new long[1];

        System.out.println("field: " + assignedField(box));
        System.out.println("element: " + assignedElement(numbers, 0));
        System.out.println("long element: "
                + assignedLongElement(longNumbers, 0));
        System.out.println("stored: " + box.value
                + ", " + numbers[0] + ", " + longNumbers[0]);
    }
}
```

Từ thư mục chứa tệp, dùng JDK 17 hoặc mới hơn:

```bash
javac -encoding UTF-8 StackInstructionDemo.java
java StackInstructionDemo
```

**Standard output chính xác:**

```text
field: 12
element: 21
long element: 34
stored: 12, 21, 34
```

Các method chỉ trả giá trị đã gán, không dùng luồng chạy phức tạp hay thời gian thực, nên bốn dòng stdout không phụ thuộc JIT. Việc `intResult()` và `longResult()` không có tác dụng phụ quan sát được ở source; tuy vậy compiler tạo bytecode từ các method invocation thông thường và có thể phải bỏ các giá trị trả về không dùng tới.

### 9.1. Xem instruction trong class file

```bash
javap -c -p StackInstructionDemo
javap -c -p 'StackInstructionDemo$Box'
```

Trong **Bash** và **PowerShell**, dấu nháy đơn bảo vệ ký tự `$` trong tên nested class; với **Windows cmd.exe**, hãy dùng dấu nháy kép thay thế. Lệnh thứ hai là tùy chọn để xem lớp `Box`.

Tìm các đoạn bytecode, **không cần khớp tuyệt đối chỉ số offset hay constant pool**:

| Method | Instruction đáng quan sát | Vì sao có thể xuất hiện |
| --- | --- | --- |
| `main` | `invokestatic ... intResult`, `pop` | Bỏ giá trị `int` không dùng |
| `main` | `invokestatic ... longResult`, `pop2` | Bỏ giá trị `long` không dùng |
| `main` | `new`, `dup`, `invokespecial ... Box.<init>` | Giữ tham chiếu object khi gọi constructor |
| `assignedField` | `dup_x1`, `putfield` | Giữ giá trị gán `int` đồng thời ghi field |
| `assignedElement` | `dup_x2`, `iastore` | Giữ giá trị gán `int` đồng thời ghi array element |
| `assignedLongElement` | `dup2_x2`, `lastore` | Giữ giá trị gán `long` (cat2) đồng thời ghi array element |

**Cần đọc kết quả `javap` trên công cụ thật.** Những instruction trong bảng là các mẫu điển hình của `javac` cho mã này, nhưng các trình biên dịch Java hoặc phiên bản/chiến lược tối ưu có thể dùng cách sắp xếp khác mà vẫn hợp lệ. `javap -c` cho thấy **bytecode trong class file**, không phải mã máy mà JIT về sau thực thi.

### 9.2. Diễn giải nhóm thao tác trên field

Với `assignedField(Box box)`, mục tiêu là vừa thực hiện `box.value = 12`, vừa giữ kết quả cho biến `result`. Có thể thấy chuỗi khái niệm:

```text
aload_0            // reference box
bipush 12          // int 12
dup_x1             // ..., ref, 12 → ..., 12, ref, 12
putfield ... value  // tiêu thụ ref, 12; còn 12
istore_1           // gán result
iload_1
ireturn
```

Nó hợp lệ bởi **reference** và **int** đều là category 1. Nếu thay field thành `long`, compiler không được mù quáng dùng cùng `dup_x1` vì top `long` là category 2; cần chọn instruction hợp lệ với dạng stack mới.

### 9.3. Diễn giải `dup2_x2` với mảng `long`

Trước `lastore`, stack có `long[]` reference, index và `long` value:

```text
..., arrayref(cat1), index(cat1), 34L(cat2)
```

`dup2_x2` form 2 làm thành:

```text
..., 34L(cat2), arrayref(cat1), index(cat1), 34L(cat2)
```

Lệnh `lastore` lấy **arrayref, index, long value ở top theo quy tắc toán hạng**; bản sao `34L` phía dưới vẫn là giá trị biểu thức gán. Cách hiểu “dup2 sao chép hai số ở trên” sẽ sai ngay trong trường hợp này vì top chỉ chứa **một** giá trị logic `long`.

## 10. Bytecode verifier ngăn các dạng stack bất hợp lệ

Các instruction trong `.class` phải đáp ứng quy tắc về **số lượng, thứ tự, category và loại giá trị**. JVM verifier kiểm tra dữ liệu kiểu của stack và local variables theo control flow trước khi cho bytecode được thực thi theo điều kiện đặc tả.

Ví dụ **bytecode giả định sai**:

```text
lconst_0   // đẩy một long: category 2
pop        // SAI: pop đòi top category 1
return
```

Đây **không phải source Java hợp lệ** và không được chạy như file `.java`; nó minh họa một class file do bytecode assembler/tool sinh **sai**. Một JVM tuân thủ đặc tả sẽ không coi thao tác đó là hợp lệ; khi xác minh có thể xuất hiện `VerifyError` (thuộc nhóm `LinkageError`). Không nên cố tạo lỗi bằng cách sửa vài byte hex trong class file thực, vì làm thế có thể sinh `ClassFormatError` do hỏng cấu trúc trước khi tới bước verification.

Tương tự:

```text
dconst_0
dup         // SAI: double là category 2
```

và:

```text
iconst_1
lconst_0
swap        // SAI: top là long (category 2)
```

Không có khái niệm “chỉ cảnh báo nhưng JVM tự sửa stack” cho những chuỗi sai category này. Verifier cũng không chỉ xem từng opcode độc lập: dữ liệu trên các nhánh hợp lưu phải có trạng thái stack và kiểu phù hợp; một `ireturn` phải trả giá trị phù hợp với descriptor của method; các lời gọi method và truy cập field cần toán hạng đúng kiểu.

## 11. Stack depth, `max_stack` và nhầm lẫn về bộ nhớ

Trong mỗi `Code` attribute, class file lưu thông tin như **`max_stack`** và **`max_locals`**. `max_stack` biểu diễn **độ sâu tối đa của operand stack tính theo đơn vị độ rộng**, không phải luôn là số lượng giá trị logic trên stack.

Ví dụ, sau một `dup2` với một `long` ở top, hai bản sao `long` chiếm **bốn đơn vị stack** tổng cộng, dù chỉ có **hai giá trị logic**. Nếu mã giữ thêm reference hoặc index phía dưới, các đơn vị tương ứng cũng cộng vào độ sâu.

Có thể xem thông tin do compiler tạo:

```bash
javap -v -p StackInstructionDemo
```

Tìm phần `Code:`, dòng `stack=...` và `locals=...`. Kết quả cụ thể phụ thuộc bytecode thực tế của compiler và method được xem. `max_stack` **không** cho biết lượng bộ nhớ heap, không bằng kích thước JVM thread stack tính theo byte, và không có nghĩa mỗi giá trị cat2 luôn chiếm đúng 8 byte vật lý trong thanh ghi/bộ nhớ của trình biên dịch JIT.

### Giá trị category 2 ở local variables

Với local-variable array, `long` và `double` chiếm **hai chỉ số slot liên tiếp** trong mô hình JVM; reference và `int` chiếm một slot. Quy tắc này liên quan cùng phân loại category, nhưng lệnh `dup2` tác động lên **operand stack**, không nhân đôi các ô local variables. Muốn đọc/ghi local variables có các instruction riêng như `lload`, `lstore`, `iload`, `istore`.

## 12. Những nhầm lẫn thường gặp

| Nhầm lẫn | Cách hiểu đúng |
| --- | --- |
| “`pop2` luôn xóa hai giá trị.” | Xóa **hai cat1** hoặc **một cat2**. |
| “`dup2` nhân đôi mọi thứ ở top theo hai object.” | Nhân đôi tổng độ rộng hai đơn vị, có thể là **một long/double**. |
| “`dup` của reference tạo object mới.” | Chỉ nhân đôi **giá trị reference**, không gọi `new`. |
| “`swap` dùng được với `long` nếu JVM 64-bit.” | Không; cả hai top value phải là category 1 theo JVM Specification. |
| “Đuôi `_x2` nghĩa là nhân đôi hai lần.” | `_x2` diễn tả chèn bản sao sâu xuống dưới **hai đơn vị**; phạm vi sao chép do `dup` hoặc `dup2` quyết định. |
| “Hai giá trị cat1 tương đương một cat2 trong mọi instruction.” | Chúng có cùng **tổng độ rộng** nhưng khác số lượng giá trị và kiểu; chỉ một số instruction có nhiều form hợp lệ. |
| “Bytecode có `dup` tức là chương trình Java cố ý tạo bản sao để dùng lại.” | Thường là chiến lược compiler giữ kết quả trung gian, chẳng hạn khi gán field, constructor hoặc mảng. |
| “`javap` cho mã CPU cuối cùng.” | Nó giải mã class-file bytecode; JIT và interpreter là bước runtime khác. |
| “Một lệnh hợp lệ về category thì cả method chắc chắn hợp lệ.” | Verifier còn xét loại giá trị, descriptor, init state, độ sâu stack và các nhánh control flow. |

## 13. Bài luyện đọc operand stack

**Bài 1:** Stack `..., int A, int B`; chạy `dup_x1`.  
**Đáp án:** `..., B, A, B`. Hai giá trị đều cat1.

**Bài 2:** Stack `..., long L`; muốn sao chép giá trị `L` cho một bước tính tiếp theo. Chọn `dup` hay `dup2`?  
**Đáp án:** `dup2`; `long` là một giá trị category 2.

**Bài 3:** Stack `..., double D, int I`; chạy `dup_x2`.  
**Đáp án:** `..., I, D, I` theo form 2 vì top là cat1 và giá trị bên dưới là cat2.

**Bài 4:** Stack `..., long L, int I`; chạy `pop2`.  
**Đáp án:** **Không hợp lệ**; top là cat1 nhưng ngay dưới là cat2. `pop2` không chia `long` thành một đơn vị để bỏ.

**Bài 5:** Stack `..., int X, int Y, long L`; chạy `dup2_x2`.  
**Đáp án:** `..., L, X, Y, L` theo form 2. Giá trị `L` cat2 được nhân đôi và chèn dưới hai cat1.

**Bài 6:** Stack `..., long X, long Y`; chạy `dup2_x2`.  
**Đáp án:** `..., Y, X, Y` theo form 4. Cả hai đều cat2; không thể thay bằng `swap`.

**Bài 7:** Sau `new` và trước constructor, compiler dùng `dup` trên reference. Có hai đối tượng được tạo không?  
**Đáp án:** Không. `dup` tạo hai bản sao cùng reference (đang ở trạng thái uninitialized được verifier theo dõi), không thực hiện cấp phát object mới.

**Bài 8:** Vì sao phép gán `long result = (array[index] = 34L)` có thể gắn với `dup2_x2`?  
**Đáp án:** Bản sao của giá trị `long` cần được giữ làm **kết quả biểu thức**, trong khi `lastore` vẫn cần bộ ba `arrayref(cat1), index(cat1), value(cat2)`.

### Tự kiểm tra sau khi học

1. Có phân biệt được **category** với **số giá trị logic** hay không?
2. Có xác định đúng **TOP** là phần bên phải trong ký hiệu `..., value2, value1` không?
3. Với `dup_x2`, có nhớ rằng **giá trị top phải là cat1** ở cả hai form không?
4. Với `dup2_x2`, có phân tích đủ **bốn form** hợp lệ không?
5. Có biết dùng `javap -c -p` để xác minh instruction mà `javac` thực sự tạo không?

**Tài liệu đối chiếu:** *The Java Virtual Machine Specification*, chương **6.5 Instructions** (các mục `pop`, `pop2`, `dup`, `dup_x1`, `dup_x2`, `dup2`, `dup2_x1`, `dup2_x2`, `swap`): https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-6.html. Khi học cú pháp các form, ưu tiên bảng operand stack của đặc tả thay vì đoán từ tên opcode.

Kết luận thực hành: đọc `pop`, `dup`, `swap` bằng cách **xác định category của từng giá trị theo thứ tự từ đáy lên đỉnh**, chọn đúng form instruction rồi viết lại stack sau thao tác. Chỉ khi toàn bộ các biến đổi này hợp lệ về category và các lệnh kế tiếp dùng đúng kiểu thì JVM mới có thể xác minh và thực thi bytecode như dự kiến.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="bytecode-instructions-005">Nhánh điều kiện, goto, vòng lặp và địa chỉ đích</a>

<details>
<summary>Xem chi tiết</summary>

# Nhánh điều kiện, `goto`, vòng lặp và địa chỉ đích trong JVM bytecode

## 1. Mã nguồn điều khiển luồng được chuyển thành bytecode như thế nào?

Trong Java, chúng ta dùng `if`, `else`, `for`, `while`, `do-while`, `switch`, `break` và `continue` để diễn tả thứ tự thực hiện. Ở mức JVM, phần lớn luồng điều khiển thông thường được biểu diễn bằng **instruction so sánh/kiểm tra điều kiện**, **instruction nhảy vô điều kiện**, **instruction lựa chọn nhiều nhánh**, cùng với những instruction thực hiện công việc và trả kết quả.

~~~text
Source Java                      Mô hình bytecode thường gặp

if (condition) { ... }       →   kiểm tra; nhảy qua block nếu sai
while (condition) { ... }    →   kiểm tra; chạy body; nhảy ngược về điều kiện
for (init; test; update)     →   init; test; body; update; nhảy để lặp
break                        →   nhảy tới điểm sau vòng lặp/switch
continue                     →   nhảy tới điểm cập nhật/kiểm tra của vòng lặp
switch                       →   tableswitch / lookupswitch / chuỗi so sánh
return                       →   ireturn, areturn, return, ... theo loại giá trị
~~~

Đây là cách hình dung, **không phải quy tắc một-một** giữa cú pháp nguồn và một lệnh bytecode cụ thể. Compiler có thể đảo chiều điều kiện, bỏ nhánh dư, constant-fold, đổi vị trí kiểm tra hoặc sinh các cấu trúc khác mà vẫn bảo toàn ngữ nghĩa của chương trình.

Một phương thức có bytecode nằm trong thuộc tính `Code` của class file. Mảng `code[]` này là một dãy byte instruction có địa chỉ tính **từ đầu code của chính phương thức đó**. Trong đầu ra `javap -c`, số ở đầu dòng như `0:`, `4:`, `12:` chính là **bytecode offset**, không phải số dòng source, index trong constant pool hay địa chỉ RAM.

## 2. Program counter (PC), fall-through và branch target

Theo mô hình JVM, mỗi JVM thread có **program counter (PC) register** để biểu thị vị trí instruction đang được thực thi trong phương thức hiện tại; phần chi tiết native method có quy tắc riêng. Khi chạy các instruction bytecode:

- **Fall-through**: nếu instruction không thay đổi luồng điều khiển, vị trí tiếp theo thường là instruction kế tiếp theo thứ tự `code[]`.
- **Conditional branch**: JVM kiểm tra điều kiện sau khi lấy operand cần thiết từ operand stack. Nếu đúng, chuyển sang **branch target**; nếu sai, tiếp tục ở instruction tiếp theo.
- **Unconditional branch**: `goto` chuyển thẳng tới target, không kiểm tra điều kiện.
- **Return/throw**: `ireturn`, `areturn`, `return`, `athrow` chuyển điều khiển ra khỏi luồng instruction bình thường của phương thức, theo các quy tắc gọi và xử lý exception.

Mỗi instruction có độ dài byte tùy opcode và operand. Vì vậy bytecode offset thường **không tăng đều từng 1**: ví dụ một lệnh `iload_0` có thể chiếm 1 byte, còn `ifeq` dạng thông thường chiếm 3 byte (opcode và hai byte offset). Không suy ra một lệnh ở offset 4 có “số thứ tự instruction = 4”.

### Địa chỉ đích của `if` và `goto` được tính từ đâu?

Đối với các branch instruction dạng offset 16 bit như `ifeq`, `if_icmpge`, `ifnull`, `goto`, operand branch offset là một số **có dấu 16 bit**, mã hóa từ hai byte theo thứ tự high byte rồi low byte. Giá trị offset được cộng với **địa chỉ của chính opcode nhảy**, không phải địa chỉ instruction ngay sau lệnh nhảy.

~~~text
target_pc = branch_opcode_pc + signed_branch_offset
~~~

Ví dụ:

~~~text
  1: ifge          7
  4: iload_0
  5: ineg
  6: ireturn
  7: iload_0
~~~

Nếu opcode `ifge` nằm ở PC = 1 và target in bởi `javap` là 7 thì độ dời (branch offset) là **7 - 1 = +6**, không phải 7 - 4. Nếu branch target nhỏ hơn PC hiện tại thì độ dời là số âm, rất thường gặp ở vòng lặp.

`javap -c` **in target PC đã tính thành số offset đích** để dễ đọc; nó không nhất thiết in trực tiếp hai byte operand đã mã hóa. Khi cần kiểm tra bytes thực tế, dùng `javap -v` để xem cấu trúc và một công cụ đọc class file dạng hex nếu cần, đồng thời nhớ rằng phần trình bày `javap` là disassembly.

**Quan trọng:** target phải chỉ đến một instruction hợp lệ trong `code[]`, không được nhảy vào giữa byte operand của instruction khác; lớp phải thỏa các quy tắc cấu trúc và verification của JVM. Đây cũng không phải “nhảy tới dòng 7 của file Java”.

## 3. Những nhóm branch instruction phổ biến

| Nhóm | Instruction thường gặp | Operand được kiểm tra |
| --- | --- | --- |
| So với 0 | `ifeq`, `ifne`, `iflt`, `ifle`, `ifgt`, `ifge` | Lấy **một giá trị int** trên operand stack để so với 0 |
| So hai int | `if_icmpeq`, `if_icmpne`, `if_icmplt`, `if_icmple`, `if_icmpgt`, `if_icmpge` | Lấy **hai giá trị int**, so sánh theo quan hệ |
| So hai reference | `if_acmpeq`, `if_acmpne` | Kiểm tra hai reference có cùng object hoặc cùng null hay không |
| So với null | `ifnull`, `ifnonnull` | Lấy một reference và kiểm tra null |
| Nhảy vô điều kiện | `goto`, `goto_w` | Không dùng điều kiện trên stack |
| Chọn nhiều nhánh | `tableswitch`, `lookupswitch` | Lấy một khóa int, chọn target phù hợp |

Các instruction `if*` **lấy operand ra khỏi operand stack** để kiểm tra, nên trạng thái stack sau kiểm tra đã thay đổi. Compiler thường phải đặt lệnh nạp giá trị như `iload`, `aload`, `iconst` hoặc phép tính tương ứng trước lệnh branch.

Chú ý các nhầm lẫn:

- `ifeq` nghĩa là **nhảy khi int bằng 0**, chứ không phải “if của source đúng”. Nếu source viết `if (flag)`, compiler có thể dùng `ifeq` để **bỏ qua thân if khi flag false**.
- `if_icmp*` so hai số `int`, không phải mọi kiểu số. Với `long`, JVM thường dùng `lcmp` trước một branch so với 0; với `float`/`double` có `fcmpl`, `fcmpg`, `dcmpl`, `dcmpg` cùng những quy tắc riêng cho NaN.
- `if_acmp*` so **reference identity**, không gọi `equals()`. Hai `String` khác object nhưng cùng nội dung có thể là `false` khi so `==`.
- `ifnull`/`ifnonnull` làm việc với reference value; `null` không phải một object có thể dereference.
- `goto` của bytecode là công cụ triển khai control flow JVM, **không chứng minh source Java có câu lệnh goto**. Java không có statement `goto`; từ khóa `goto` được dành riêng nhưng không sử dụng.

## 4. Ví dụ tối thiểu: một if chuyển thành nhánh thế nào?

Nguồn Java:

~~~java
static int absoluteLike(int number) {
    if (number < 0) {
        return -number;
    }
    return number;
}
~~~

Một chuỗi bytecode điển hình do `javac` sinh ra là:

~~~text
0: iload_0
1: ifge          7
4: iload_0
5: ineg
6: ireturn
7: iload_0
8: ireturn
~~~

Đây là **một cách biên dịch minh họa**; hãy dùng `javap` trên class thật để biết offset và instruction của compiler bạn đang chạy.

Phân tích từng bước:

1. `iload_0` đưa `number` vào operand stack.
2. `ifge 7` lấy `number` khỏi stack và nhảy tới offset 7 **nếu number ≥ 0**, tức là **đảo điều kiện** để bỏ qua nhánh xử lý số âm.
3. Nếu `number < 0`, JVM tiếp tục ở offset 4, nạp lại `number`, thực hiện `ineg` rồi `ireturn`.
4. Nếu `number ≥ 0`, JVM tới offset 7, nạp `number` và `ireturn`.

Điều kiện source `number < 0` không bắt buộc xuất hiện thành instruction `iflt`. Compiler dùng `ifge` vì đây là điều kiện để **nhảy ra khỏi nhánh true**.

Một ghi chú về toán học: giá trị `Integer.MIN_VALUE` không có số đối dương biểu diễn được bằng `int`; phép `-number` trên `int` cho lại giá trị đó do overflow theo quy tắc Java. Ví dụ tập trung vào bytecode branching, không triển khai hàm trị tuyệt đối an toàn cho mọi `int`.

## 5. Vòng lặp = nhánh tiến, nhánh lùi và điểm kiểm tra

Một **backward branch** là branch có target offset **nhỏ hơn** offset opcode nhảy: đó là kiểu liên kết thường xuất hiện ở vòng lặp. Nhưng bản chất một vòng lặp là **chu trình trong control-flow graph (CFG)**, không nhất thiết luôn có đúng một instruction `goto` lùi.

### 5.1. While

Source:

~~~java
int i = 0;
while (i < 3) {
    i++;
}
~~~

Mô hình CFG:

~~~text
      [khởi tạo i = 0]
              |
              v
       [kiểm tra i < 3] ------ sai -----> [sau vòng lặp]
              |
             đúng
              v
          [i tăng]
              |
              +----------- nhảy về kiểm tra
~~~

Một dạng bytecode giản lược, các offset chỉ mang tính mô tả:

~~~text
0: iconst_0
1: istore_1
2: iload_1
3: iconst_3
4: if_icmpge     13
7: iinc          1, 1
10: goto         2
13: ...          ; lệnh sau vòng lặp
~~~

Lệnh `if_icmpge` nhảy **tiến** qua body khi điều kiện `i < 3` sai. `goto 2` nhảy **lùi** về vị trí kiểm tra, tạo chu trình.

**Không ghi nhớ những offset trên như output thật**: compiler có thể thay đổi cách sinh code tùy ngữ cảnh, version, phần code sau vòng lặp và tối ưu. Khi phân tích một class thực tế, offset phải lấy trực tiếp từ `javap -c`.

### 5.2. Do-while

Trong `do { body } while (condition);`, body phải chạy **ít nhất một lần**. Compiler thường đặt **body trước kiểm tra**, rồi dùng conditional branch lùi nếu điều kiện còn đúng. Điều này giải thích vì sao đôi khi chỉ có một branch có điều kiện lùi, không cần một `goto` lùi riêng.

### 5.3. For

Với `for (initialization; condition; update) { body }`, luồng logic là:

~~~text
initialization
      |
      v
  condition ----- false -----> exit
      |
     true
      v
     body
      |
      v
    update
      |
      +----------------------> condition
~~~

Phần initialization diễn ra một lần, condition trước mỗi lượt (nếu có), update sau mỗi lượt kết thúc bình thường hoặc khi `continue` chuyển tới update. Compiler có thể dùng `iinc` cho `i++`, nhưng đây chỉ là lựa chọn tối ưu cho một số biến `int` cục bộ.

Một `for (;;)` không có điều kiện có thể trở thành chu trình backedge không có nhánh thoát trực tiếp, trừ khi thân chứa `break`, `return`, `throw` hay sự kiện điều khiển khác.

### 5.4. `break` và `continue` tương ứng đích khác nhau

Trong vòng lặp Java:

- `break` thường chuyển điều khiển tới **điểm sau vòng lặp**.
- `continue` chuyển điều khiển tới **điểm thực hiện update** trong `for`, hoặc **điểm kiểm tra điều kiện** của `while`/`do-while`.
- Với **labeled break/continue**, target phải tương ứng với statement/vòng lặp được gắn nhãn theo ngữ nghĩa Java. Không phải luôn nhảy tới vòng lặp bên trong cùng.
- Nếu `break`/`continue` đi ra khỏi vùng `try/finally`, compiler phải thực hiện ngữ nghĩa `finally` phù hợp; không thể mô hình hóa bằng một `goto` duy nhất mà bỏ cleanup.

Không đồng nhất `break` trong `switch` statement với `break` thoát vòng lặp; chúng điều khiển các statement khác nhau. `switch` expression với arrow/yield cũng có quy tắc nguồn riêng.

## 6. `tableswitch` và `lookupswitch`: chọn nhiều địa chỉ đích

Với nhiều lựa chọn trên một khóa `int` phù hợp, compiler có thể dùng một trong hai switch instruction JVM.

### 6.1. `tableswitch`

`tableswitch` biểu diễn các target ứng với một **dải khóa liên tục** từ `low` đến `high`, cùng target mặc định. Từng khóa trong dải có một offset (nếu source không khai báo case tương ứng, compiler vẫn có thể cho entry đó nhảy tới default).

Cấu trúc về mặt ý nghĩa:

~~~text
tableswitch
    default_offset
    low
    high
    jump_offsets[high - low + 1]
~~~

Nó phù hợp với những giá trị `case` tương đối dày đặc; một khoảng quá rộng và thưa có thể làm bảng lãng phí bytecode.

### 6.2. `lookupswitch`

`lookupswitch` lưu danh sách **match-offset pair** cho các khóa cụ thể, cùng default:

~~~text
lookupswitch
    default_offset
    npairs
    (match_1, offset_1)
    (match_2, offset_2)
    ...
~~~

Danh sách `match` được sắp tăng theo quy tắc class-file format. Đây có thể là lựa chọn gọn hơn khi case rời rạc (ví dụ 1, 1000, 1000000).

### 6.3. Điều mà hai instruction có chung

- Chúng lấy khóa `int` từ operand stack rồi chọn một nhánh.
- Offset đích của switch được lưu dưới dạng **signed 32-bit**, tính **tương đối từ offset của opcode `tableswitch`/`lookupswitch`**, không phải từ vị trí entry trong bảng.
- Các phần dữ liệu của switch cần **padding để trường dữ liệu bắt đầu ở ranh giới 4 byte** tính từ đầu mảng `code[]`. Do đó một `tableswitch` hoặc `lookupswitch` có độ dài byte thay đổi theo vị trí opcode.
- Đầu ra `javap -c` in các target cuối cùng dưới dạng PC và thường hiển thị `default:` / `case:`, không bắt người đọc cộng offset bằng tay.
- Compiler **không bắt buộc** dùng `tableswitch` cho mọi switch dày đặc hay `lookupswitch` cho mọi switch thưa. Chính sách lựa chọn là chi tiết compiler; switch với `String` hoặc một số cấu trúc nguồn có thể dùng hash/equals hoặc chuỗi so sánh kết hợp các instruction khác.

Ví dụ Java:

~~~java
static String classify(int value) {
    return switch (value) {
        case 0 -> "ZERO";
        case 1, 2 -> "SMALL";
        default -> "OTHER";
    };
}
~~~

Khi xem `javap -c`, chú ý các target dùng chung của case 1 và 2, nhánh default và vị trí trả String. Đây là ví dụ có nhiều lựa chọn, nhưng cách compiler hạ `switch` **không được cam kết** là một opcode switch cụ thể.

## 7. Bài thực hành Java 17+: nhiều cấu trúc điều khiển trong một file

Tạo tệp **`BranchFlowDemo.java`**:

~~~java
public final class BranchFlowDemo {
    static int absoluteLike(int number) {
        if (number < 0) {
            return -number;
        }
        return number;
    }

    static String grade(int score) {
        if (score < 0) {
            return "INVALID";
        }
        if (score >= 80) {
            return "HIGH";
        }
        return "NORMAL";
    }

    static int sumEvenUntilSeven(int limit) {
        int sum = 0;
        for (int i = 0; i < limit; i++) {
            if ((i & 1) != 0) {
                continue;
            }
            if (i > 6) {
                break;
            }
            sum += i;
        }
        return sum;
    }

    static int countDown(int start) {
        int remaining = start;
        while (remaining > 0) {
            remaining--;
        }
        return remaining;
    }

    static String classify(int code) {
        return switch (code) {
            case 0 -> "ZERO";
            case 1, 2 -> "SMALL";
            default -> "OTHER";
        };
    }

    public static void main(String[] args) {
        System.out.println("abs=" + absoluteLike(-3));
        System.out.println("grade=" + grade(85));
        System.out.println("sum=" + sumEvenUntilSeven(10));
        System.out.println("remaining=" + countDown(3));
        System.out.println("kind0=" + classify(0));
        System.out.println("kind2=" + classify(2));
        System.out.println("kind9=" + classify(9));
    }
}
~~~

Biên dịch và chạy bằng JDK 17+ từ thư mục chứa file:

~~~bash
javac --release 17 BranchFlowDemo.java
java BranchFlowDemo
~~~

**Stdout được xác định bởi chương trình:**

~~~text
abs=3
grade=HIGH
sum=12
remaining=0
kind0=ZERO
kind2=SMALL
kind9=OTHER
~~~

Kiểm tra nhanh:

- `absoluteLike(-3)` trả 3 qua nhánh xử lý số âm.
- `grade(85)` rơi vào nhánh `score >= 80`, trả `HIGH`.
- Trong `sumEvenUntilSeven(10)`, số lẻ bị `continue` bỏ qua; cộng 0 + 2 + 4 + 6 = **12**; tại i = 8, `break` thoát vòng lặp.
- `countDown(3)` giảm còn 0 rồi thoát do `remaining > 0` sai.
- `classify` trả đúng string cho case 0, case 1/2 và default.

### Dùng javap để quan sát nhánh thật

~~~bash
javap -classpath . -c -p BranchFlowDemo
javap -classpath . -v -p BranchFlowDemo
~~~

Hãy đọc từng method theo cách sau:

1. Xác định block `Code:` của `absoluteLike`; tìm `ifge` hoặc một nhánh điều kiện tương đương rồi các lệnh `ireturn`.
2. Trong `grade`, chú ý nhiều điều kiện và các instruction return của kiểu reference như `areturn`.
3. Trong `sumEvenUntilSeven`, tìm điều kiện của `for`, nhánh bỏ qua số lẻ, nhánh thoát khi `i > 6` và backedge. Liên hệ `continue` với đường tới update/kiểm tra tiếp theo.
4. Trong `countDown`, tìm phần kiểm tra dừng và backedge, đồng thời xem liệu compiler có đặt điều kiện ở đầu hay dùng bố trí khác.
5. Trong `classify`, quan sát xem compiler chọn `tableswitch` hay `lookupswitch` hoặc một chuỗi nhánh; đừng suy diễn lựa chọn từ source mà không kiểm tra.
6. Với `-v`, xem thêm `StackMapTable` (nếu có), `LineNumberTable` và dữ liệu class-file version. Class biên dịch bằng `--release 17` có **major version 61**.

Một bài tập bổ sung là biên dịch riêng với `-g:none` và so đầu ra `javap -c` với `javap -l`: bỏ debug line/local metadata **không xóa những nhánh điều khiển cần thiết để chương trình chạy**.

## 8. Đọc control-flow graph (CFG) từ bytecode

**Basic block** là dãy instruction có một điểm bắt đầu và không có nhánh rẽ nội bộ, kết thúc bằng một instruction chuyển luồng hoặc tại vị trí trước một target của nhánh. Có thể dựng CFG từ `javap`:

1. Lấy **entry PC = 0** cho method có code.
2. Tìm các target của `if*`, `goto`, `tableswitch`, `lookupswitch` và các điểm tiếp tục sau conditional branch.
3. Tách basic block ở đầu method, tại target, và tại instruction tiếp sau những nơi luồng điều khiển có thể phân nhánh.
4. Nối các cạnh **conditional true**, **fall-through**, **unconditional goto**, và cạnh lựa chọn switch/default. `return` kết thúc nhánh thực thi bình thường.
5. Tìm đường quay lại các block đã có để nhận diện vòng lặp; tìm block sau nhiều nhánh hội tụ để hiểu phần tiếp tục chung.
6. Khi phân tích chương trình thực, bổ sung **exceptional control flow** từ `exception_table`; code ném exception có thể chuyển tới handler mà không qua lệnh `goto` thông thường.

Ví dụ CFG tối giản:

~~~text
PC 0: [load x; test x]
          |
          +-- true ---> PC 8: [return A]
          |
          +-- false --> PC 4: [return B]
~~~

Con số PC chỉ là nhãn offset của ví dụ. Nếu nhìn thấy đường đi `PC 20 -> PC 4`, bạn nhận ra **backedge** nhưng chưa thể khẳng định nguồn dùng `for`, `while` hay `do-while`; nhiều cấu trúc khác nhau có thể sinh CFG tương tự.

### Điều kiện rẽ nhánh không phải “kết quả boolean trên stack” trong mọi trường hợp

Một câu source như `a < b` có thể được compiler thực hiện bằng `iload` hai biến và `if_icmpge` đảo chiều để bỏ qua body, **không nhất thiết phải đẩy ra một đối tượng Boolean** hoặc một giá trị boolean tạm rồi mới kiểm tra. Điều đó giúp bạn đọc code hiệu quả hơn: quan sát **chuỗi instruction lấy operand và nhảy**, thay vì cố tìm một `Boolean` object cho mọi biểu thức điều kiện.

## 9. `&&`, `||` và short-circuit

Ngôn ngữ Java có short-circuit:

- `a && b`: nếu `a` false, **không đánh giá `b`**.
- `a || b`: nếu `a` true, **không đánh giá `b`**.

Trình biên dịch có thể sử dụng nhiều branch liên tiếp để thực thi đúng thứ tự và tránh side effect không cần thiết. Ví dụ:

~~~java
static boolean allowed(String value) {
    return value != null && !value.isEmpty();
}
~~~

Điều kiện `value != null` phải được xử lý trước, và khi `value == null`, lời gọi `value.isEmpty()` **không được thực hiện**. Ở bytecode, bạn có thể thấy `ifnull` hoặc branch tương đương chuyển sang đường trả false trước instruction `invokevirtual` của `isEmpty`.

Do đó, nếu bạn muốn phân tích hành vi của một expression phức tạp, hãy xác định các đường nhánh thực tế và thứ tự lời gọi, không chỉ đếm số instruction `if`.

## 10. Giới hạn branch offset và `goto_w`

Hầu hết conditional branch thông thường và `goto` sử dụng **signed 16-bit relative offset**, với khoảng biểu diễn từ -32768 đến +32767 tính từ bytecode PC của opcode. Khi target ở ngoài khoảng này:

- `goto_w` sử dụng **signed 32-bit branch offset** để nhảy vô điều kiện tới target xa hơn.
- Không có một cặp opcode `if_w` chung cho mọi điều kiện. Compiler có thể **đảo điều kiện** để nhảy qua một `goto_w`, nhờ đó vẫn biểu diễn được một nhánh điều kiện dài.
- Cách compiler triển khai phụ thuộc kích thước method và công cụ; đừng suy ra lệnh `goto_w` xuất hiện vì nguồn Java có cấu trúc đặc biệt.
- Một method có giới hạn độ dài `code[]` theo class-file format (tối đa **65535 byte** ở các phiên bản class file liên quan), nên không thể có code bytecode dài vô hạn trong một method. Compiler có thể báo lỗi khi phương thức quá lớn; việc dùng `goto_w` không loại bỏ giới hạn kích thước method.

Ví dụ minh họa biến đổi để nhảy xa:

~~~text
Điều kiện dự định: nếu x == 0 thì nhảy tới TARGET_FAR

Dạng có thể được compiler chuyển thành:
    ifne AFTER
    goto_w TARGET_FAR
AFTER:
    ...           ; tiếp tục khi x != 0
~~~

Đây là **pseudocode điều khiển**, không phải output `javap` cố định; các nhãn `AFTER`, `TARGET_FAR` chỉ giúp người học hiểu cách đảo điều kiện.

### Các instruction lịch sử

JVM còn có `jsr`, `jsr_w` và `ret`, từng được dùng với subroutine bytecode cho `finally` trong các compiler cũ. Chúng **không phải** cách biểu diễn vòng lặp Java thông thường; đối với class-file version hiện đại, các ràng buộc verifier đã khiến compiler Java hiện nay không dùng mô hình subroutine lịch sử này. Khi thấy tài liệu cũ có `jsr`/`ret`, hãy phân biệt với `goto` và compiler Java hiện đại.

## 11. Verification, `StackMapTable` và branch target an toàn

JVM verifier không chỉ kiểm tra opcode có được hỗ trợ. Nó phải xác minh điều kiện để các instruction dùng operand stack/local variables nhất quán ở những điểm điều khiển có thể tới.

Ví dụ: một block nhận điều khiển từ hai nhánh phải có trạng thái kiểu operand stack/local variables **hợp lệ và tương thích** theo quy tắc verification. Nếu một nhánh để trên stack giá trị int trong khi đường khác đưa reference tới cùng điểm mà code đòi đọc int, class file có thể không qua verification.

**`StackMapTable`** (với các class-file version hiện đại khi thích hợp) chứa các frame hỗ trợ type checking ở các vị trí liên quan. Đây là metadata dành cho **verification**, khác với `LineNumberTable` hỗ trợ ánh xạ dòng source:

- Không thể chỉ sửa hai byte branch offset rồi tin rằng class sẽ chạy; target, stack frame, instruction length và control-flow metadata phải nhất quán.
- Nhảy vào giữa instruction/operand không hợp lệ.
- Target thuộc phạm vi `Code` của **cùng method**; muốn gọi method khác phải dùng instruction invoke, không dùng `goto` để “nhảy” thẳng sang một method khác.
- `StackMapTable` không mô tả thứ tự nguyên văn của các câu `if`/loop source. Nó hỗ trợ kiểm tra tính nhất quán kiểu của bytecode đã sinh.

Bên cạnh các branch instruction còn có **exception table** trong `Code`, với những giá trị `start_pc`, `end_pc`, `handler_pc` và kiểu catch. Các trường này là **PC index trong mảng code**, không phải tất cả đều là signed relative offset giống operand `goto`. Exception có thể chuyển điều khiển tới handler dù trong bytecode không có một `goto` minh thị tới đó.

## 12. Địa chỉ đích trong javap không phải địa chỉ máy hay dòng source

Có ít nhất bốn dạng số thường xuất hiện khi phân tích class file:

| Giá trị | Nó đánh số gì? | Công dụng |
| --- | --- | --- |
| Bytecode offset/PC | Vị trí instruction trong `code[]` của **method** | `javap -c` hiển thị lệnh và target |
| Branch offset | Số byte đích lệch tương đối so với PC opcode nhảy | Operand của `if*`, `goto`, `switch` |
| Source line number | Dòng văn bản `.java` do compiler ghi trong `LineNumberTable` | Breakpoint, stack trace, debug |
| Địa chỉ native/virtual memory | Vị trí trong không gian địa chỉ của tiến trình hoặc code do JIT sinh | Chi tiết implementation/runtime, không phải nhãn `javap -c` |

Ví dụ một dòng disassembly `10: goto 2` nói rằng **instruction ở bytecode PC 10 chuyển tới bytecode PC 2** trong cùng method. Nó **không** có nghĩa “nhảy tới dòng source 2”, “truy cập địa chỉ RAM 0x2”, hoặc “tới instruction thứ 2”. Nếu bytecode đang được JIT tối ưu thành native machine code, vị trí instruction native hoàn toàn khác hệ tọa độ PC bytecode này.

Một chương trình có thể chạy với class file đã bị strip debug metadata nên không có `LineNumberTable`, nhưng nhánh `goto` và các bytecode offset vẫn hiện diện vì JVM cần chúng để thực thi. Điều này là bằng chứng trực tiếp cho thấy **bytecode control flow** và **source-line mapping** là hai lớp thông tin khác nhau.

## 13. Những nhầm lẫn phổ biến

1. **“`ifeq` nghĩa là source if đúng.”** Sai. Nó nhảy khi operand int bằng 0; compiler thường dùng để bỏ qua body khi điều kiện nguồn false.
2. **“`goto 12` là nhảy tới dòng 12 trong Java.”** Sai. Đây là bytecode PC 12 của cùng method.
3. **“Branch offset tính từ sau instruction `goto`.”** Sai. JVM branch offset được tính từ **PC của chính opcode**.
4. **“Vòng lặp luôn phải có một `goto` lùi.”** Sai. CFG có thể có conditional backedge, hoặc compiler bố trí điều kiện và jump khác.
5. **“`break` chỉ cần xóa instruction tiếp theo.”** Sai. Nó chuyển điều khiển đúng target và vẫn phải tôn trọng ngữ nghĩa `finally` khi có.
6. **“`continue` trong for luôn quay về đầu body.”** Sai. Nó đi tới update/kiểm tra như cấu trúc `for` quy định.
7. **“`if_acmpeq` gọi `equals()`.”** Sai. Nó so reference identity.
8. **“`tableswitch` có offset tính từ đầu bảng case.”** Sai. Offset relative được tính từ **PC opcode switch**.
9. **“Mọi switch Java là `tableswitch`.”** Sai. Compiler có thể chọn `lookupswitch` hoặc cơ chế khác.
10. **“Nhảy tới bất kỳ byte nào trong `code[]` đều được.”** Sai. Target và stack state phải hợp lệ theo verification.
11. **“Dòng 20 trong `LineNumberTable` là PC 20.”** Sai. Line number và bytecode offset là hai đại lượng khác nhau.
12. **“Source Java có từ khóa `goto` để gọi JVM goto.”** Sai. Java dành riêng từ `goto` nhưng không có statement `goto` hợp lệ.

## 14. Câu hỏi và bài tập tự kiểm tra

1. `ifge` ở PC 4 nhảy tới PC 16. Operand relative offset bằng bao nhiêu? **+12**, vì tính từ PC opcode = 4.
2. `goto` ở PC 20 nhảy về PC 5. Offset bằng bao nhiêu? **-15**; đây là một backward branch.
3. `ifeq` nhìn thấy int 0 sẽ làm gì? **Nhảy tới target**; nếu khác 0, fall-through.
4. Một `if (x > 0)` có thể biên dịch thành `ifle` không? **Có**, nếu `ifle` được dùng để bỏ qua body khi điều kiện nguồn sai.
5. `if_acmpeq` so `String.equals()` hay so reference identity? **So identity**.
6. Tại sao `continue` trong `for (init; test; update)` thường dẫn về `update`? Vì đó là bước tiếp theo theo ngữ nghĩa của `for`, trước lần test tiếp theo.
7. Tại sao `tableswitch` thường hiệu quả với dải khóa dày và `lookupswitch` hợp hơn với khóa thưa? Bởi **bảng target liên tục** khác với **danh sách cặp match/offset**.
8. `StackMapTable` có phải tên khác của `LineNumberTable` không? **Không**; một bảng phục vụ verification kiểu, một bảng phục vụ ánh xạ dòng nguồn.
9. Nếu `javap -c` hiển thị `0: iload_0`, `1: ifge 7`, `4: iload_0`, tại sao PC kế tiếp là 4? Vì `ifge` dài 3 byte, không phải 1 byte.
10. Tại sao một decompiler không thể chắc source dùng `for` hay `while` chỉ từ backedge? Vì nhiều cấu trúc nguồn có thể tạo **CFG/bytecode tương đương**.

## 15. Nguồn tham khảo đặc tả

- JVM Specification Java SE 17, chương **Instructions**: https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-6.html
- JVMS, chương **The Class File Format**, cấu trúc `Code` và `StackMapTable`: https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-4.html
- JVMS, chương **Verification**: https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-4.html#jvms-4.10
- Tài liệu JDK 17 `javap`: https://docs.oracle.com/en/java/javase/17/docs/specs/man/javap.html
- Java Language Specification Java SE 17, phần **Statements**: https://docs.oracle.com/javase/specs/jls/se17/html/jls-14.html

**Kết luận kỹ thuật:** `if*`, `goto`, `goto_w`, `tableswitch` và `lookupswitch` mô tả **các cạnh luồng điều khiển giữa những vị trí bytecode trong cùng phương thức**. Conditional branch kiểm tra operand stack, `goto` nhảy không điều kiện và vòng lặp tạo chu trình trong CFG. Các địa chỉ đích được tính theo **bytecode PC và signed relative offset**, không phải số dòng Java hay địa chỉ bộ nhớ vật lý. Khi đọc disassembly, luôn lần theo operand, target và trạng thái stack để hiểu chính xác JVM thực thi đường đi nào.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="bytecode-instructions-006">tableswitch và lookupswitch trong biểu diễn switch</a>

<details>
<summary>Xem chi tiết</summary>

# `tableswitch` và `lookupswitch`: cách JVM biểu diễn lệnh `switch`

Khi thấy câu lệnh hoặc biểu thức `switch` trong Java, bạn đang đọc **cú pháp ngôn ngữ**. Khi mở tệp `.class` bằng `javap -c`, bạn có thể thấy hai opcode chuyên dụng của JVM: **`tableswitch`** và **`lookupswitch`**. Chúng đều chọn một nhánh dựa trên một giá trị `int`, nhưng lưu các nhánh theo hai cấu trúc khác nhau.

- **`tableswitch`** lưu bảng nhảy cho **mọi giá trị nguyên trong một đoạn liên tục** từ `low` đến `high`.
- **`lookupswitch`** lưu **danh sách các cặp `match → target`** đã sắp xếp tăng dần theo khóa.

Sự khác nhau giải thích vì sao switch với case gần nhau thường dùng `tableswitch` còn case thưa thường dùng `lookupswitch`. Đây là **xu hướng tối ưu hóa của trình biên dịch**, không phải lời hứa rằng Java Language Specification bắt buộc chọn opcode nào cho một đoạn mã nguồn nhất định.

Bài học tập trung vào cơ chế bytecode, cách đọc dữ liệu bằng `javap`, xử lý `default`, các giá trị khuyết, biểu diễn ở class file, và các giới hạn khi suy luận từ mã Java. Ví dụ chạy được với **Java 17+**.

## 1. Đi từ `switch` của Java đến lệnh JVM

Mã Java:

```java
static int level(int code) {
    return switch (code) {
        case 1 -> 10;
        case 2 -> 20;
        case 3 -> 30;
        default -> -1;
    };
}
```

Đây là **đoạn phương thức minh họa**, không phải chương trình độc lập. Trong file `.class` do `javac` tạo, JVM không còn xử lý từ khóa `case` hay `->` của Java source. Nó thực thi bytecode do compiler sinh ra, thường theo chuỗi:

```text
load biến/tham số chứa khóa
        │
        ▼
  operand stack: [int key]
        │
        ▼
  tableswitch HOẶC lookupswitch
        │
        ├─ case phù hợp → lệnh tại target tương ứng
        └─ không phù hợp → lệnh tại target default
                │
                ▼
     thực thi mã của nhánh rồi tiếp tục/trả về
```

**Cả hai opcode đều tiêu thụ một giá trị loại `int` ở đỉnh operand stack.** Chúng không trực tiếp nhận `String`, `enum`, `long` hay một đối tượng tùy ý làm khóa. Nếu switch Java có kiểu khác, trình biên dịch phải áp dụng bước chuyển đổi/ánh xạ hoặc sinh một cấu trúc điều khiển khác.

### 1.1. So sánh nhanh

| Tiêu chí | `tableswitch` | `lookupswitch` |
| --- | --- | --- |
| Opcode | **170**, hexadecimal `0xaa` | **171**, hexadecimal `0xab` |
| Khóa trên operand stack | Một `int` | Một `int` |
| Dữ liệu chính | `low`, `high`, rồi `high-low+1` offsets | `npairs`, rồi `npairs` cặp `match, offset` |
| Giá trị được mô tả | Mọi số nguyên trong đoạn `[low, high]` | Chỉ các khóa `match` đã liệt kê |
| Case không khai báo | Mục bảng có thể trỏ tới nhánh mặc định | Không có cặp match, dùng nhánh mặc định |
| Truy cập bảng về mặt thiết kế | Kiểm tra cận rồi index bằng `key-low` | Tìm một khóa trong danh sách match |
| Độ dài dữ liệu | Theo **độ rộng của đoạn khóa** | Theo **số khóa được liệt kê** |
| Tình huống compiler thường chọn | Các case tương đối dày | Các case cách xa nhau |

Hai lệnh **không ghi sẵn kết quả của từng case**; chúng ghi **địa chỉ nhảy tương đối** tới những bytecode thực thi thân nhánh. Nhiều khóa khác nhau có thể cùng nhảy tới một target.

## 2. `tableswitch`: bảng nhảy theo đoạn khóa liên tục

Với các case `3, 4, 5, 6`:

```java
static String classify(int code) {
    return switch (code) {
        case 3 -> "THREE";
        case 4 -> "FOUR";
        case 5 -> "FIVE";
        case 6 -> "SIX";
        default -> "UNKNOWN";
    };
}
```

Compiler có thể tổ chức một bảng:

```text
index value   jump-table index   bytecode target
3             0                  case 3
4             1                  case 4
5             2                  case 5
6             3                  case 6
other         —                  default
```

Mô tả bytecode điển hình khi dùng `tableswitch`:

```text
iload_0
tableswitch { // 3 to 6
    3: <target case 3>
    4: <target case 4>
    5: <target case 5>
    6: <target case 6>
    default: <target default>
}
```

**Các nhãn `<target ...>` là ký hiệu giải thích**, không phải các bytecode offsets thực tế; `javap` sẽ hiển thị số offset của chương trình đã biên dịch.

### 2.1. Thuật toán ở mức ngữ nghĩa lệnh

Khi `tableswitch` thực thi:

1. Lấy và loại bỏ một `int index` khỏi operand stack.
2. Nếu `index < low` hoặc `index > high`, chọn offset `default`.
3. Ngược lại, tính **`position = index - low`** và lấy offset tại vị trí đó trong jump table.
4. Cộng offset đã chọn với **địa chỉ opcode `tableswitch`** để xác định địa chỉ lệnh đích.
5. Tiếp tục thực thi ở địa chỉ lệnh đích.

Mã giả:

```text
key = pop_int()
if (key < low || key > high) {
    relativeOffset = defaultOffset
} else {
    relativeOffset = jumpOffsets[key - low]
}
nextPc = switchOpcodePc + relativeOffset
```

Đây là mô tả **ngữ nghĩa**, không áp đặt JVM/JIT phải hiện thực bằng đúng chuỗi phép tính máy đó.

### 2.2. Case bị khuyết vẫn phải có mục bảng

Giả sử source chỉ có `1, 3, 5`:

```java
static String oddLabel(int x) {
    return switch (x) {
        case 1 -> "ONE";
        case 3 -> "THREE";
        case 5 -> "FIVE";
        default -> "OTHER";
    };
}
```

Nếu compiler chọn `tableswitch` với `low = 1`, `high = 5`, nó phải có **5 entries**, dù source chỉ có 3 case:

```text
key:         1        2         3         4         5
entry:   case 1    default   case 3    default   case 5
```

Các case bị khuyết **không có một nhánh Java riêng**; các entries tương ứng có thể trỏ tới cùng target `default`. Đây là chi tiết quan trọng khi ước lượng kích thước bảng: `high-low+1` có thể lớn hơn nhiều so với số case.

**Ví dụ cực đoan:** nếu chỉ có case `1` và `1_000_000`, `tableswitch` phải bao phủ khoảng **1 triệu entries** nếu chọn cùng đoạn này. Vì lãng phí, compiler thường nghiêng về `lookupswitch` hoặc phương án khác.

### 2.3. Kích thước phần dữ liệu của `tableswitch`

Định dạng của instruction:

```text
tableswitch (1 byte opcode: 0xaa)
  0..3 byte padding
  default (4 byte signed offset)
  low     (4 byte signed int)
  high    (4 byte signed int)
  jump offsets[high - low + 1]   (4 byte mỗi offset)
```

Với `N = high-low+1`:

```text
total bytes = 1 + padding + 12 + 4*N
```

Đây là **độ dài lệnh trong bytecode**, chưa tính mã các case sau bảng và metadata khác của class file. Ví dụ `low=3, high=6` có `N=4`, nên dùng **29 đến 32 byte** cho bản thân instruction, tùy 0–3 byte padding.

`low` và `high` phải thỏa `low <= high`. Mọi đích nhảy trong bảng và `default` phải trỏ tới **opcode của lệnh hợp lệ trong chính phương thức**.

## 3. `lookupswitch`: danh sách khóa thưa và các cặp match–offset

Ví dụ:

```java
static String sparse(int code) {
    return switch (code) {
        case -100 -> "NEGATIVE";
        case 7 -> "SEVEN";
        case 10_000 -> "BIG";
        default -> "OTHER";
    };
}
```

Nếu dùng `lookupswitch`, bytecode lưu chỉ **ba khóa xuất hiện**:

```text
match        relative jump target
-100         case -100
7            case 7
10000        case 10000
(default)    default
```

Phần trình bày tương ứng trong `javap -c` thường giống:

```text
iload_0
lookupswitch { // 3
    -100: <target NEGATIVE>
       7: <target SEVEN>
   10000: <target BIG>
 default: <target OTHER>
}
```

Khác với `tableswitch`, không có entries cho các số giữa `7` và `10000`. Vì thế cấu trúc này thường tiết kiệm dung lượng khi phạm vi khóa rộng nhưng số case ít.

### 3.1. Ngữ nghĩa của `lookupswitch`

Khi JVM thực thi:

1. Pop một khóa `int` từ operand stack.
2. Tìm giá trị `match` bằng khóa trong danh sách cặp đã lưu.
3. Nếu thấy match, lấy offset đi kèm; nếu không, lấy `default`.
4. Đích nhảy bằng **địa chỉ opcode `lookupswitch` + offset đã chọn**.
5. Tiếp tục từ opcode tại đích.

```text
key = pop_int()
pair = lookup_match(key)
relativeOffset = pair.found ? pair.offset : defaultOffset
nextPc = switchOpcodePc + relativeOffset
```

**Các `match` phải được sắp xếp tăng dần theo giá trị số nguyên có dấu.** Đây là **ràng buộc của class-file format**, không chỉ là lựa chọn trình bày của `javap`. JVM hoặc implementation có thể khai thác tính có thứ tự để tìm kiếm hiệu quả, nhưng **JVMS không bắt buộc** một thuật toán tìm kiếm cụ thể như binary search.

### 3.2. Kích thước dữ liệu của `lookupswitch`

```text
lookupswitch (1 byte opcode: 0xab)
  0..3 byte padding
  default (4 byte signed offset)
  npairs  (4 byte signed int, không âm)
  match_0 (4 byte signed int), offset_0 (4 byte signed int)
  match_1 (4 byte signed int), offset_1 (4 byte signed int)
  ...
```

Với `P = npairs`:

```text
total bytes = 1 + padding + 8 + 8*P
```

Với 3 case, instruction chiếm **33–36 byte**, tùy padding. Một `tableswitch` bao phủ khoảng một triệu giá trị rõ ràng không phải lựa chọn lưu trữ tiết kiệm trong tình huống này.

`npairs` có thể bằng 0 trong định dạng lệnh hợp lệ theo JVMS. Trường hợp đó không có match pair; mọi khóa đi theo nhánh `default`. Tuy nhiên compiler Java bình thường không nhất thiết sinh ra một `lookupswitch` như thế cho source switch rỗng.

## 4. Vì sao các lệnh cần padding 0–3 byte?

Bytecode là luồng opcode và operands có độ dài biến thiên. Hai lệnh `tableswitch` và `lookupswitch` đặc biệt vì **các trường 32-bit bên trong phải bắt đầu tại địa chỉ chia hết cho 4, tính từ đầu mã của phương thức**.

- Sau **1 byte opcode** có thể có **0, 1, 2 hoặc 3 byte đệm**.
- Byte đầu của trường `default` phải ở offset chia hết cho 4 trong mảng `Code.code[]`.
- Việc đệm căn chỉnh **các operands nội bộ**, không yêu cầu địa chỉ opcode của switch chia hết cho 4.
- Các số 32-bit của hai instruction được mã hóa big-endian theo định dạng JVM.
- Các byte padding không phải case mới hay target; khi đọc file nhị phân phải bỏ qua chính xác số byte đệm.

Nếu opcode bắt đầu tại chỉ số `p` trong mảng code, số padding:

```text
padding = (4 - ((p + 1) % 4)) % 4
```

Ví dụ:

| Opcode bắt đầu ở `p` | Offset ngay sau opcode | Padding | `default` bắt đầu |
| ---: | ---: | ---: | ---: |
| 0 | 1 | 3 | 4 |
| 1 | 2 | 2 | 4 |
| 2 | 3 | 1 | 4 |
| 3 | 4 | 0 | 4 |
| 4 | 5 | 3 | 8 |

Tất cả offsets ở bảng tính **từ đầu bytecode của phương thức**, không phải từ đầu toàn tệp `.class`. Không được dùng địa chỉ tuyệt đối của file để tính padding.

### 4.1. Đích nhảy là offset tương đối từ OPCODE switch

Đây là lỗi phổ biến khi tự viết disassembler:

```text
Nếu tableswitch/lookupswitch opcode bắt đầu tại code offset 12
và một jump offset có giá trị +40
→ đích là code offset 52

KHÔNG phải:
  12 + 1 + số padding + 40
  hay vị trí ngay sau toàn instruction + 40
```

Ngay cả khi `default` là giá trị âm, địa chỉ đích vẫn được tính **switchOpcodePc + signedOffset**. Mọi target được tính ra phải là **vị trí bắt đầu opcode hợp lệ thuộc phương thức đó**, không được trỏ vào giữa operand, padding hoặc instruction khác.

Điều này đặc biệt quan trọng khi instrumentation sửa bytecode: thêm/bớt một lệnh trước switch có thể đổi padding; thay cấu trúc switch còn đòi cập nhật offsets, các nhánh khác và frame verification metadata liên quan.

## 5. Compiler chọn `tableswitch` hay `lookupswitch` như thế nào?

Với các `case` nguyên, một tiêu chí tự nhiên là **mật độ của tập khóa**:

```text
rangeSize = maxCase - minCase + 1
caseCount = số giá trị case riêng biệt
density = caseCount / rangeSize
```

Nếu `rangeSize` gần `caseCount`, bảng nhảy ít có khoảng trống. Nếu `rangeSize` rất lớn mà `caseCount` nhỏ, việc lưu offsets cho từng giá trị trong đoạn sẽ lãng phí bộ nhớ class file.

Về chi phí **chỉ của bản thân instruction** và bỏ qua padding:

```text
tableswitch: 1 + 12 + 4 * rangeSize
lookupswitch: 1 + 8 + 8 * caseCount
```

So sánh này hữu ích để hiểu trade-off về **bytecode size**, nhưng **không phải công thức bắt buộc** để quyết định compiler sẽ dùng instruction nào. Compiler có thể xét nhiều yếu tố hoặc dùng heuristic riêng; sự lựa chọn thay đổi giữa phiên bản/toolchain hoặc khi có tối ưu hóa khác.

Chẳng hạn với case `1, 2, 3, 4`:

- `rangeSize = 4`, `caseCount = 4`.
- `tableswitch` có phần dữ liệu dày và ít lỗ trống.
- Compiler `javac` thường chọn `tableswitch`.

Với case `-100, 7, 10000`:

- `rangeSize = 10101`, `caseCount = 3`.
- `tableswitch` sẽ cần hơn mười nghìn offsets.
- Compiler `javac` thường chọn `lookupswitch`.

**Không có “ngưỡng 50%” hoặc “trên 5 case luôn dùng tableswitch” được Java Language Specification bảo đảm.** Kết luận chắc chắn cho class đang chạy phải đến từ **bytecode thực tế**.

### 5.1. `tableswitch` có nhất thiết nhanh hơn?

Không thể khẳng định “mỗi `tableswitch` luôn nhanh hơn mỗi `lookupswitch`” trong chương trình thật:

- `tableswitch` hỗ trợ tra cứu theo chỉ số, nhưng có thể làm instruction lớn nếu vùng khóa rộng.
- `lookupswitch` tiết kiệm dữ liệu với khóa thưa nhưng cần tìm match.
- JVM interpreter có thể thực thi trực tiếp hai lệnh theo cách triển khai riêng.
- **JIT compiler** có thể chuyển chúng thành machine-code jump tables, các phép so sánh có nhánh, cây quyết định hoặc cách khác.
- Hiệu năng thực phụ thuộc độ phân bố khóa, cache, branch prediction, lượng mã, profile runtime và mã trong từng nhánh.

Vì vậy khi tối ưu ứng dụng, dùng profiler/benchmark phù hợp; không viết code rối để ép opcode nếu chưa chứng minh lợi ích. Trong source, ưu tiên `switch` dễ hiểu và chính xác trước.

## 6. Ví dụ Java 17+: hai biểu diễn trong cùng chương trình

Tạo tệp **`SwitchOpcodesDemo.java`**:

```java
public class SwitchOpcodesDemo {

    static String dense(int value) {
        return switch (value) {
            case 1 -> "ONE";
            case 2 -> "TWO";
            case 3 -> "THREE";
            case 4 -> "FOUR";
            default -> "OTHER";
        };
    }

    static String sparse(int value) {
        return switch (value) {
            case -100 -> "NEGATIVE";
            case 7 -> "SEVEN";
            case 10_000 -> "BIG";
            default -> "OTHER";
        };
    }

    public static void main(String[] args) {
        System.out.println("dense(3) = " + dense(3));
        System.out.println("dense(8) = " + dense(8));
        System.out.println("sparse(7) = " + sparse(7));
        System.out.println("sparse(5) = " + sparse(5));
    }
}
```

Biên dịch/chạy bằng JDK 17 trở lên:

```bash
javac --release 17 SwitchOpcodesDemo.java
java SwitchOpcodesDemo
```

**Stdout xác định:**

```text
dense(3) = THREE
dense(8) = OTHER
sparse(7) = SEVEN
sparse(5) = OTHER
```

Hai phương thức đều trả kết quả theo ngữ nghĩa Java; không có khác biệt bắt buộc về hành vi bên ngoài. Khác biệt đáng chú ý nằm ở cách compiler chọn lưu bảng nhảy trong bytecode.

### 6.1. Dùng `javap` kiểm tra chính xác

```bash
javap -c -p SwitchOpcodesDemo
```

Với `javac` JDK 17 và hình dạng case này, thông thường:

- Phương thức `dense` có `tableswitch` bao phủ **1 đến 4**.
- Phương thức `sparse` có `lookupswitch` với **3 cặp match** mang khóa **-100, 7, 10000**.
- Phần thân mỗi case có các thao tác đẩy `String` literal và `areturn`; tùy compiler có thể có thêm nhánh/metadata khác.
- `javap` sẽ in địa chỉ opcode/target cụ thể. Không tự đặt số offset trước khi xem output từ compiler đã dùng.

Một bản trích **minh họa các trường khóa, không phải toàn bộ dump với offsets cố định**:

```text
dense(int):
  iload_0
  tableswitch { // 1 to 4
       1: <ONE>
       2: <TWO>
       3: <THREE>
       4: <FOUR>
  default: <OTHER>
  }

sparse(int):
  iload_0
  lookupswitch { // 3
      -100: <NEGATIVE>
         7: <SEVEN>
     10000: <BIG>
   default: <OTHER>
  }
```

Đọc dump theo cách sau:

1. **`iload_0`:** đưa tham số `int value` lên operand stack.
2. **`tableswitch` / `lookupswitch`:** pop khóa, chọn một target.
3. **Target `case`:** tải chuỗi tương ứng rồi trả về.
4. **Target `default`:** tải `"OTHER"` rồi trả về.
5. Kiểm tra offsets được in để thấy các target thực nằm **sau instruction switch**, dù instruction đó dài hơn một byte.

### 6.2. Quan sát class-file version riêng với opcode

`--release 17` tạo bytecode theo target Java 17. Kiểm tra:

```bash
javap -v -p SwitchOpcodesDemo
```

Các dòng version kỳ vọng:

```text
minor version: 0
major version: 61
```

Bản thân `tableswitch` và `lookupswitch` **không phải preview features**. Chúng là các opcode đã có từ các thế hệ JVM trước. Vì vậy không cần `--enable-preview` để chạy switch biểu thức Java 17 hợp lệ trong ví dụ.

## 7. Với case lỗ trống, `default` và nhiều case chung thân thì sao?

### 7.1. Không có `default` tường minh trong switch statement

Một `switch statement` có thể hợp lệ mà không khai báo nhánh `default`:

```java
static void printIfKnown(int value) {
    switch (value) {
        case 1:
            System.out.println("ONE");
            break;
        case 2:
            System.out.println("TWO");
            break;
    }
}
```

Đoạn này là một phương thức minh họa. Trong class file, **cả `tableswitch` lẫn `lookupswitch` luôn có trường `default offset`** theo định dạng lệnh. Khi source không có `default`, compiler có thể trỏ default tới code **sau switch**, để bỏ qua toàn bộ các nhánh.

**Không có `default` trong Java source** không có nghĩa **bytecode switch instruction thiếu operand default**.

### 7.2. Nhiều nhãn case dùng cùng target

```java
static String category(int x) {
    return switch (x) {
        case 1, 2, 3 -> "SMALL";
        case 100 -> "LARGE";
        default -> "OTHER";
    };
}
```

Compiler có thể cho các khóa 1/2/3 cùng nhảy tới đoạn code sinh chuỗi `SMALL`. Dữ liệu ở bảng có **nhiều khóa** nhưng target trùng nhau; JVMS không yêu cầu từng case có một đoạn mã mới.

Trong `tableswitch`, nhiều entries có thể mang cùng offset; trong `lookupswitch`, nhiều cặp `match` khác nhau có thể có offset giống nhau.

### 7.3. `fall-through` trong switch statement cổ điển

```java
static int countFlags(int x) {
    int result = 0;
    switch (x) {
        case 1:
            result += 1;
        case 2:
            result += 2;
            break;
        default:
            result = -1;
    }
    return result;
}
```

- `countFlags(1)` trả **3**, vì sau case 1 luồng điều khiển **đi tiếp** vào case 2.
- `countFlags(2)` trả **2**.
- `countFlags(3)` trả **-1**.

`tableswitch`/`lookupswitch` chỉ quyết định **đích nhảy ban đầu**, còn **fall-through** do các lệnh tuần tự sau target quyết định. Chính trình biên dịch quản lý bố trí thân nhánh và `goto`/`break`; opcode switch không có một “flag fall-through” riêng.

### 7.4. Switch expression và `yield`

Switch expression (Java 14+) yêu cầu sinh ra một **giá trị**. Với arrow cases, compiler thường đặt giá trị nhánh trên operand stack và đi tới điểm hội tụ hoặc return. Nếu dùng khối `{ ... yield value; }`, `yield` là cú pháp source, không phải opcode bytecode có tên `yield`.

```java
static int points(int n) {
    return switch (n) {
        case 1 -> 10;
        case 2 -> {
            int value = 20;
            yield value;
        }
        default -> -1;
    };
}
```

`tableswitch` hoặc `lookupswitch` vẫn chỉ nhảy theo khóa nguyên; chúng **không quyết định trực tiếp giá trị trả về**. Giá trị do bytecode ở target nhánh tạo ra.

## 8. Switch trên kiểu nào? Vì sao không phải lúc nào có opcode switch?

Hai opcode nhận **`int`**. Nhưng Java hỗ trợ các dạng switch phong phú hơn.

| Switch source | Cách biểu diễn/giới hạn thường gặp |
| --- | --- |
| `int` / `byte` / `short` / `char` | Có thể trực tiếp dùng `tableswitch` hoặc `lookupswitch` trên khóa kiểu `int` của JVM |
| `Integer`, `Byte`, `Short`, `Character` | Thường unbox (và chuyển kiểu phù hợp) để dùng khóa `int`; `null` có thể gây `NullPointerException` trong switch truyền thống |
| `enum` | Compiler chuyển giá trị enum thành khóa nguyên (có thể qua mapping synthetic hoặc ordinal) rồi chọn nhánh |
| `String` | Compiler thường dùng `hashCode()`, `equals()` và một/nhiều switch trên giá trị nguyên; không có opcode `stringswitch` |
| Switch pattern (Java 21+ chuẩn) | Có thể dùng `invokedynamic`/bootstrap runtime matching và các nhánh integer sau đó; không thể suy một opcode cố định chỉ từ từ khóa `switch` |
| `long`, `float`, `double` thông thường ở Java 17 | Không phải kiểu selector được cho phép cho switch theo Java 17 |

Không nhầm khái niệm **kiểu Java của biểu thức switch** với **loại dữ liệu đầu vào của instruction JVM**. String và enum có thể là switch Java hợp lệ nhưng không có nghĩa `tableswitch` tự so sánh chuỗi hoặc đối tượng enum.

### 8.1. Switch trên `String` thường cần bước tra cứu khác

Ví dụ Java 17:

```java
static int stringCode(String text) {
    return switch (text) {
        case "ON" -> 1;
        case "OFF" -> 0;
        default -> -1;
    };
}
```

`javac` có thể sinh code dựa trên hash của chuỗi, sau đó kiểm tra `equals` để xử lý **hash collisions** đúng ngữ nghĩa. Một hoặc nhiều opcode switch trên khóa `int` có thể xuất hiện trong mã kết quả, nhưng **không suy luận trực tiếp vị trí nhánh từ giá trị hash** như thể hash là duy nhất.

Nếu `text == null` trong switch `String` Java 17 thông thường, thao tác với selector có thể ném `NullPointerException`. Với pattern switch phiên bản mới, quy tắc `case null` có thể khác. Phải xem **version và hình thức switch cụ thể** trước khi đưa kết luận về `null`.

### 8.2. Switch trên enum không cần opcode cho kiểu đối tượng

Enum Java có các hằng có tên, nhưng JVM switch instructions vẫn nhận `int`. Compiler có thể tạo mã ánh xạ ordinal → mã trường hợp; chi tiết codegen là lựa chọn của compiler, và binary compatibility khi enum thay đổi cũng phụ thuộc cách mã được biên dịch.

Không nên chỉnh reorder enum nhằm “ép tableswitch” khi chưa kiểm tra ảnh hưởng nghiệp vụ và binary compatibility.

## 9. Mã máy JIT có giữ nguyên kiểu jump table không?

`javap` cho thấy **bytecode trước khi JVM tối ưu hóa runtime**, không phải chắc chắn là các lệnh máy CPU thực sự thực thi.

Sau khi JVM nhận profile, JIT compiler có thể:

- Dùng jump table máy cho nhóm case dày.
- Tạo một chuỗi so sánh/rẽ nhánh cho các khóa thưa.
- Sắp xếp lại nhánh dựa trên profile, inline thân case hoặc đơn giản hóa code.
- Loại bỏ switch khi compiler suy ra khóa là một hằng tại vị trí gọi.
- Thực thi theo interpreter trước rồi dùng compiled machine code sau.

Không có bảo đảm 1–1 rằng `tableswitch` thành “một lệnh CPU” hoặc `lookupswitch` nhất thiết luôn dùng binary search ở runtime. **Bytecode là hợp đồng ngữ nghĩa với JVM**; chiến lược chạy thật tùy JVM/JIT/CPU.

Nếu muốn đo, benchmark phải có đủ warm-up, nhiều đầu vào và tránh constant folding/dead-code elimination. Với Java, một framework như JMH thường thích hợp hơn viết một vòng `System.nanoTime()` ngắn rồi kết luận opcode nào nhanh hơn.

## 10. Bytecode verifier và tính hợp lệ của bảng nhảy

Khi tự sinh hoặc chỉnh sửa class file (ASM, Byte Buddy, instrumentation), không thể tùy ý đưa dữ liệu vào opcode switch.

### 10.1. Các bất biến quan trọng

Đối với **`tableswitch`**:

- Operand stack phải có một khóa `int` phù hợp.
- `low <= high`.
- Phải có đúng **`high - low + 1`** jump offsets.
- Các offsets, kể cả `default`, phải dẫn đến **đầu một instruction opcode hợp lệ thuộc phương thức hiện tại**.
- Vùng padding phải được đặt đúng để trường `default` thẳng hàng bốn byte theo offset của code array.

Đối với **`lookupswitch`**:

- Operand stack phải có một khóa `int` phù hợp.
- `npairs >= 0`; số cặp dữ liệu khớp với `npairs`.
- Các giá trị `match` phải **tăng nghiêm ngặt theo số nguyên có dấu**, không xếp theo chuỗi văn bản.
- Mỗi offset phải trỏ tới opcode hợp lệ trong phương thức, không trỏ vào giữa instruction hoặc padding.
- Padding phải đặt đúng trước `default`.

Ngoài định dạng, các nhánh sau khi merge còn phải có trạng thái operand stack, local variables và `StackMapTable` nhất quán theo quy tắc verification của class file tương ứng.

### 10.2. Một lỗi offset khi chèn logging

Giả sử một agent thêm bytecode logging ngay trước `tableswitch`:

```text
Trước:
  ... bytecode
  tableswitch (opcode ở offset p; padding theo p)

Sau khi chèn 1–3 byte:
  ... bytecode mới
  tableswitch (opcode ở offset p + delta; có thể đổi padding)
```

Nếu chỉ sao chép nguyên operands cũ, cấu trúc switch có thể bị lệch; các offset nhảy và các cấu trúc phụ thuộc code layout khác cũng phải được tính lại.

Trong thực hành, nên dùng bytecode library đã hiểu cấu trúc switch và tính toán frame/offset phù hợp. Khi gặp `VerifyError` hoặc `ClassFormatError` sau instrumentation, kiểm tra việc alignment, target và type state thay vì quy lỗi cho logic `switch` của Java source.

## 11. Quan sát khi gỡ lỗi và tối ưu hóa

Các lệnh hữu ích:

```bash
# Biên dịch ví dụ theo Java 17
javac --release 17 SwitchOpcodesDemo.java

# Xem instruction và nhãn target
javap -c -p SwitchOpcodesDemo

# Xem thêm max_stack, max_locals, StackMapTable, version...
javap -v -p SwitchOpcodesDemo

# Thực thi để kiểm tra hành vi observable
java SwitchOpcodesDemo
```

Quy trình đọc:

1. Tìm đúng phương thức `dense`/`sparse` và bytecode tương ứng; không lẫn `main` với phương thức cần phân tích.
2. Xác minh opcode switch xuất hiện sau lệnh đẩy selector (ví dụ `iload_0`).
3. Với `tableswitch`, ghi `low`, `high`, case còn thiếu và đích `default`.
4. Với `lookupswitch`, ghi `npairs`, danh sách khóa đã sort và đích `default`.
5. Theo target tới các lệnh sinh giá trị/chạy side effects/`goto`/`return`, không dừng ở đoạn bảng nhảy.
6. Nếu mục tiêu là hiệu năng, dùng profiling/benchmark; nếu mục tiêu là lỗi, đối chiếu bytecode thực tế và JVM/spec.

### 11.1. Bảng phân tích triệu chứng

| Dấu hiệu | Hướng kiểm tra |
| --- | --- |
| `javap` không có `tableswitch` dù case liền kề | Compiler heuristic, switch được tối ưu thành chuỗi `if`/`goto` hoặc hình thức khác |
| Có `tableswitch` nhưng trong source thiếu một số case | Các mục bị khuyết có thể cùng trỏ tới `default` |
| Switch trên String nhưng thấy `lookupswitch` theo số nguyên | Đó có thể là lookup theo hash, phải kiểm tra `equals` và các nhánh xử lý collision |
| Các case có thứ tự khác với source | `lookupswitch` lưu match theo thứ tự tăng dần; bố trí body có thể khác thứ tự khai báo |
| Branch đích nhìn như một số byte offset khó hiểu | `javap` hiển thị địa chỉ tương đối đầu phương thức; trong class file offsets của switch tính từ opcode |
| Class bị từ chối sau khi bytecode instrumentation | Kiểm tra số entry, offset, padding, `StackMapTable` và quy tắc verification |
| Switch chậm dù có `tableswitch` | Profiler để xác nhận thời gian nằm ở lookup, thân case, boxing, I/O hay cache/branch effects |
| Benchmark giữa Java versions cho kết quả khác nhau | JIT, compiler, layout, profile, options và môi trường phần cứng có thể khác |

## 12. Những hiểu nhầm thường gặp

1. **“`switch` trong Java luôn dịch thành một opcode switch.”** Sai. Compiler có thể dùng `tableswitch`, `lookupswitch` hoặc kết hợp các instruction khác, đặc biệt với `String`, enum và pattern switch.
2. **“`tableswitch` chứa đúng số case viết trong source.”** Sai. Nó phải có một entry cho **mọi giá trị trong `low..high`**, kể cả khoảng trống.
3. **“`lookupswitch` có thể lưu match pairs theo thứ tự tùy ý.”** Sai. JVMS yêu cầu các `match` được sắp xếp tăng dần.
4. **“`default` của bytecode có thể bỏ đi nếu source không khai báo.”** Sai. Cả hai instruction luôn chứa offset `default`.
5. **“Switch jump offset tính từ cuối instruction.”** Sai. Offset được cộng với **địa chỉ opcode switch**.
6. **“Các bytes padding nằm trong bảng case.”** Sai. Chúng chỉ phục vụ căn chỉnh dữ liệu 32-bit của instruction.
7. **“`tableswitch` chỉ dùng được khi mọi case trong đoạn đều khai báo.”** Sai. Các mục bị khuyết có thể trỏ tới `default`.
8. **“`lookupswitch` luôn sử dụng binary search.”** Sai. JVMS mô tả ngữ nghĩa; thuật toán tra cứu là tùy implementation.
9. **“`tableswitch` chắc chắn nhanh hơn `lookupswitch`.”** Sai khi suy rộng tới JVM/JIT và workload cụ thể; cần đo.
10. **“`long` hay `double` có thể là khóa của hai instruction.”** Sai. Chúng pop một **`int`** từ operand stack; switch Java 17 không cho dùng các kiểu này làm selector thông thường.
11. **“Thứ tự case trên source bắt buộc bằng thứ tự mã máy.”** Sai. Compiler/JIT được phép bố trí khác nhưng phải giữ đúng hành vi.
12. **“`fall-through` được bật bằng một flag trong `tableswitch`.”** Sai. Fall-through xuất phát từ luồng lệnh sau target, không phải metadata opcode.

## 13. Tự kiểm tra (có đáp án)

**Câu 1.** Với `tableswitch low=-2, high=2`, bảng jump offsets có bao nhiêu entry, dù source chỉ có case `-2, 0, 2`?

**Đáp án:** `2 - (-2) + 1 = 5` entries cho `-2,-1,0,1,2`. Hai giá trị không khai báo có thể trỏ tới `default`.

**Câu 2.** `lookupswitch` có các keys `9, -10, 100`. Chúng phải được lưu theo thứ tự nào?

**Đáp án:** `-10, 9, 100` (tăng dần theo `int` có dấu); thứ tự source không quyết định thứ tự cặp match trong bytecode.

**Câu 3.** Nếu `tableswitch` opcode nằm ở code offset 10, cần mấy byte padding để `default` bắt đầu tại offset chia hết cho 4?

**Đáp án:** Sau opcode là 11; cần **1 byte** để `default` bắt đầu ở offset 12.

**Câu 4.** Một `lookupswitch` ở offset 12 có một relative jump offset `-8`. Địa chỉ target là bao nhiêu?

**Đáp án:** `12 + (-8) = 4`, miễn offset 4 đúng là đầu opcode hợp lệ trong cùng phương thức.

**Câu 5.** Vì sao `switch` trên String trong bytecode có thể hiển thị `lookupswitch` nhưng JVM không có khóa chuỗi?

**Đáp án:** Compiler đã biến so khớp chuỗi thành bước dùng khóa nguyên (ví dụ hash), phối hợp so sánh `equals` để bảo toàn ngữ nghĩa kể cả collision. `lookupswitch` chỉ làm việc với `int`.

**Câu 6.** Với ba khóa `1, 2, 3`, `javac` bắt buộc sinh `tableswitch` không?

**Đáp án:** Không. Mật độ cao làm `tableswitch` thường phù hợp, nhưng JVMS/JLS không buộc compiler dùng một opcode cụ thể.

**Câu 7.** Câu lệnh switch thiếu `default` trong source có thể tạo bytecode `tableswitch` thiếu trường `default` không?

**Đáp án:** Không. Trường `default` là bắt buộc trong định dạng instruction; compiler trỏ nó tới đường điều khiển phù hợp, chẳng hạn đoạn code sau switch.

**Câu 8.** Vì sao chỉ nhìn `javap -c` không đủ kết luận hiệu năng switch trên một bản HotSpot cụ thể?

**Đáp án:** `javap` hiển thị bytecode; HotSpot/JIT có thể biên dịch/biến đổi thành machine code khác. Cần dữ liệu workload/profiler/benchmark.

## 14. Kết nối kiến thức: source, class file và runtime

```text
Java source switch
        │
        ▼
javac chọn chiến lược lowering
        │
        ├─ bảng dày      → tableswitch (low..high + offsets)
        ├─ khóa thưa     → lookupswitch (sorted match-offset pairs)
        └─ kiểu phức tạp → chuyển đổi/so sánh/dispatch khác
        │
        ▼
.class — bytecode có jump offsets hợp lệ, padding đúng
        │
        ▼
JVM verifier / interpreter / JIT
        │
        ▼
Hành vi quan sát được theo ngữ nghĩa Java
```

**Ghi nhớ:** `tableswitch` đánh index vào một đoạn khóa nguyên; `lookupswitch` tìm match trong danh sách khóa nguyên đã sắp xếp. Cả hai pop một `int`, có target `default` bắt buộc, mã hóa jump offsets tương đối với **địa chỉ opcode switch** và cần padding cho các operands 32-bit. Cách một `switch` trong source được biểu diễn phụ thuộc compiler; cách chạy thật có thể tiếp tục được JIT tối ưu hóa.

### Tài liệu tham khảo

- [JVMS Java SE 17, 6.5 — tableswitch](https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-6.html#jvms-6.5.tableswitch): định dạng, jump table, `low/high`, `default` và offsets.
- [JVMS Java SE 17, 6.5 — lookupswitch](https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-6.html#jvms-6.5.lookupswitch): `npairs`, thứ tự match, cơ chế dispatch.
- [JVMS Java SE 17, 2.11 — Instruction set summary](https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-2.html#jvms-2.11): opcode operands và yêu cầu padding.
- [JVMS Java SE 17, 3.10 — Compiling Switches](https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-3.html#jvms-3.10): minh họa chuyển switch Java thành `tableswitch`/`lookupswitch`.
- [JVMS Java SE 17, 4.9 — Constraints on Java Virtual Machine Code](https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-4.html#jvms-4.9): các ràng buộc cấu trúc/nhảy.
- [JDK 17 `javap` manual](https://docs.oracle.com/en/java/javase/17/docs/specs/man/javap.html): cách xem bytecode.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="bytecode-instructions-007">new, newarray, anewarray và multianewarray khi tạo dữ liệu</a>

<details>
<summary>Xem chi tiết</summary>

# `new`, `newarray`, `anewarray` và `multianewarray` khi tạo dữ liệu trong JVM

## 1. Bốn opcode giải quyết bốn tình huống cấp phát

Trong mã nguồn Java, từ khóa `new` xuất hiện cả khi tạo một instance lẫn khi tạo mảng. Nhưng ở **bytecode JVM**, có nhiều lệnh tạo dữ liệu với trách nhiệm khác nhau. Cần phân biệt **từ khóa Java** và **opcode JVM**.

| Biểu thức Java | Opcode cấp phát thường thấy từ `javac` | Thứ được tạo |
| --- | --- | --- |
| `new Person("Lan")` | `new`, tiếp theo `invokespecial` để gọi `<init>` | Một đối tượng instance của class |
| `new int[3]` | `newarray` | Một đối tượng mảng có phần tử `int` |
| `new String[3]` | `anewarray` | Một đối tượng mảng có phần tử tham chiếu `String` |
| `new int[2][3]` | `multianewarray` | Outer array và các mảng con của hai chiều được chỉ định |
| `new int[2][]` | `anewarray` với component type `int[]` | Chỉ outer array; các hàng ban đầu `null` |

Bốn lệnh đều có thể **tạo đối tượng trên heap và đẩy một reference lên operand stack**. Khác biệt nằm ở kiểu đối tượng, cách mã hóa operand, số kích thước mảng được cung cấp và việc có cần gọi constructor instance sau cấp phát hay không.

```text
                      Cú pháp Java dùng new
                                |
             +------------------+------------------+
             |                                     |
      new Person(...)                         new Type[...]
             |                                     |
            new                   +-----------------+-----------------+
                                  |                 |                 |
                            primitive[]       reference[]     nhiều chiều
                                  |                 |                 |
                               newarray         anewarray       multianewarray
```

Đây là cách dịch thường gặp; compiler có thể sinh những chuỗi bytecode hợp lệ khác cho cùng ngữ nghĩa. Vì vậy muốn biết một tệp `.class` thực sự dùng lệnh nào, hãy kiểm tra bằng `javap -c`.

## 2. Mô hình chung: heap, operand stack và constant pool

JVM là máy ảo dựa trên ngăn xếp. Khi cấp phát:

1. Bytecode cung cấp **kiểu cần cấp phát** qua operand mã hóa ngay sau opcode, ví dụ `atype` hoặc một constant-pool index.
2. Những dữ liệu động như **độ dài mảng `count`** được nạp lên **operand stack** trước instruction.
3. Instruction kiểm tra các điều kiện liên quan, cấp phát array/object trong mô hình **heap** và đẩy **reference** kết quả lên operand stack.
4. Instruction tiếp theo như `astore` có thể lưu reference ấy vào một local slot.

Ví dụ:

```text
iconst_3       stack: []  → [3]
newarray int   stack: [3] → [ref(int[3])]
astore_1       stack: [ref(int[3])] → []
               locals[1] giữ reference tới mảng
```

**Mảng không nằm toàn bộ bên trong operand stack.** Stack chỉ chứa giá trị tham chiếu sau cấp phát. Mảng thật có các phần tử, độ dài và kiểu runtime trong vùng dữ liệu dành cho object.

Một số thuật ngữ cần tách:

- **Opcode:** mã thao tác, ví dụ `newarray` hoặc `multianewarray`. Mỗi opcode được mã hóa bằng một byte.
- **Operand bytes:** dữ liệu mã hóa theo sau opcode, chẳng hạn chỉ số constant pool, `atype`, `dimensions`.
- **Operand stack:** những giá trị runtime mà instruction đọc/tiêu thụ, như `count`. `count` **không** nhất thiết nằm trong operand bytes của instruction.
- **Local variables:** các slot trong frame phương thức, thường giữ reference sau lệnh `astore`.
- **Constant pool:** dữ liệu class file có các biểu tượng kiểu mà `new`, `anewarray` và `multianewarray` tham chiếu.

Ví dụ, `newarray int` có operand byte chỉ loại `int`, nhưng **độ dài** là một giá trị `int` ở operand stack. Đây là chỗ người mới học thường nhầm.

## 3. `new` — tạo instance của class

### 3.1. Định dạng JVM

`new` có dạng:

```text
new indexbyte1 indexbyte2
```

- Opcode là **`0xbb`**.
- Hai byte đi sau tạo thành **constant-pool index unsigned 16-bit**.
- Entry được tham chiếu là `CONSTANT_Class_info` mô tả class sẽ được instantiate.
- Instruction không tiêu thụ `count` như các lệnh tạo mảng.
- Khi thành công, nó cấp phát một instance rồi đẩy **reference với trạng thái chưa hoàn tất khởi tạo** theo quy tắc verifier.

Ví dụ mã nguồn:

```java
final class Person {
    final String name;

    Person(String name) {
        this.name = name;
    }
}

// Minh họa bên trong một phương thức:
Person p = new Person("Lan");
```

Bytecode minh họa:

```text
new           #k     // class Person
dup
ldc           #s     // String "Lan"
invokespecial #m     // Method Person."<init>":(Ljava/lang/String;)V
astore_1
```

Các chỉ số `#k`, `#s`, `#m` và local slot là **placeholder minh họa**, không phải đầu ra byte-for-byte cố định của mọi `javac`.

### 3.2. Tại sao `new` không đồng nghĩa với gọi constructor?

Cần tách ba bước:

1. **Cấp phát instance:** `new` tạo object và các trường có giá trị mặc định theo mô hình JVM.
2. **Khởi tạo class:** nếu class chưa được khởi tạo và quy tắc yêu cầu, JVM thực hiện class initialization, trong đó có các khối `static` phù hợp.
3. **Khởi tạo instance:** `invokespecial` thường gọi `<init>` để hoàn tất constructor; compiler bảo đảm gọi super-constructor và các hoạt động initialization theo quy tắc Java.

Nếu constructor ném ngoại lệ, biểu thức `new Person(...)` không kết thúc bình thường. Verifier theo dõi **uninitialized reference** và không cho dùng nó tùy tiện như một instance đã khởi tạo.

### 3.3. Tại sao thường xuất hiện `dup`?

`invokespecial <init>` tiêu thụ receiver cùng các tham số trên operand stack. Nhưng biểu thức `new Person("Lan")` vẫn cần trả về reference tới object sau constructor. `dup` thường được dùng để giữ lại một reference cho bước lưu kết quả:

```text
new Person          → [uninitializedRef]
dup                 → [uninitializedRef, uninitializedRef]
ldc "Lan"           → [uninitializedRef, uninitializedRef, "Lan"]
invokespecial <init>→ [initializedRef]   (khi thành công)
astore_1            → []                 (local 1 giữ reference)
```

Đây là sơ đồ stack điển hình, không phải quy tắc rằng mọi phép cấp phát đều phải đi qua cùng một dãy lệnh.

### 3.4. `new` không dùng để tạo interface, abstract instance hoặc mảng

Không thể trực tiếp instantiate interface hoặc abstract class:

```java
// Các dòng sau chỉ minh họa lỗi biên dịch:
// Runnable r = new Runnable();
// java.lang.Number n = new java.lang.Number();
```

Mảng `int[]` và `String[]` cũng không được tạo bằng cách dùng opcode `new` hướng tới một array class như instance bình thường. Chúng có những opcode tạo mảng riêng.

Nếu class file sinh không đúng và yêu cầu instantiate class không phù hợp, có thể gặp `InstantiationError` hoặc những lỗi liên kết/xác minh liên quan. Với Java source thông thường, nhiều trường hợp bị compiler chặn sớm.

## 4. `newarray` — tạo mảng primitive một chiều

### 4.1. Định dạng và hiệu ứng stack

```text
newarray atype
```

- Opcode byte: **`0xbc`**.
- `atype`: một byte biểu thị **primitive component type**.
- Operand stack **trước lệnh**: phải có một giá trị `int` là `count`.
- Instruction lấy `count`, tạo **một mảng primitive** và đẩy reference của mảng.
- Nếu `count < 0` thì ném `NegativeArraySizeException`.

```text
iconst_3       stack []  → [3]
newarray int   stack [3] → [ref(int[3])]
```

### 4.2. Bảng đầy đủ các giá trị `atype`

| Giá trị | Tên trong JVMS | Primitive component type | Mã nguồn ví dụ |
| ---: | --- | --- | --- |
| 4 | `T_BOOLEAN` | `boolean` | `new boolean[2]` |
| 5 | `T_CHAR` | `char` | `new char[2]` |
| 6 | `T_FLOAT` | `float` | `new float[2]` |
| 7 | `T_DOUBLE` | `double` | `new double[2]` |
| 8 | `T_BYTE` | `byte` | `new byte[2]` |
| 9 | `T_SHORT` | `short` | `new short[2]` |
| 10 | `T_INT` | `int` | `new int[2]` |
| 11 | `T_LONG` | `long` | `new long[2]` |

Không có mã `T_STRING` hay `T_OBJECT` trong bảng này. Nếu component type là reference, ta cần cơ chế tạo mảng reference.

### 4.3. Phần tử ban đầu có gì?

Khi cấp phát một array object, các phần tử được thiết lập **giá trị mặc định của kiểu thành phần**:

| Mảng | Giá trị mặc định phần tử |
| --- | --- |
| `int[]`, `byte[]`, `short[]` | 0 |
| `long[]` | `0L` |
| `float[]` | `0.0f` |
| `double[]` | `0.0d` |
| `boolean[]` | `false` |
| `char[]` | ký tự `'\u0000'` |

`new int[3]` có một object mảng với ba phần tử `0`. Nó **không** tạo ba instance của lớp wrapper `Integer` và không có constructor cho mỗi primitive element.

Mặc dù phần tử là primitive, **bản thân mảng là một object**: `Object o = new int[3];` hợp lệ; `int[]` có `length` và có kiểu runtime.

## 5. `anewarray` — tạo mảng có phần tử reference

### 5.1. Định dạng

```text
anewarray indexbyte1 indexbyte2
```

- Opcode byte: **`0xbd`**.
- Hai operand bytes tạo constant-pool index tới `CONSTANT_Class_info` mô tả **component type**.
- Operand stack cung cấp `count` kiểu `int`.
- Kết quả là một **reference array** có độ dài `count`.
- Tất cả phần tử ban đầu mang **giá trị null**, chưa tham chiếu tới instance nào.

Ví dụ:

```java
String[] names = new String[3];
Runnable[] jobs = new Runnable[3];
Number[] values = new Number[3];
```

`Runnable` là interface và `Number` là abstract class, nhưng mảng của chúng vẫn hợp lệ: các slot chỉ chứa **reference**, không bắt buộc phải tạo instance của component type.

### 5.2. Cấp phát mảng không gọi constructor của phần tử

```java
Person[] people = new Person[2];
System.out.println(people[0] == null); // true
```

**Đoạn code là minh họa**, giả định một class `Person` đã được khai báo. Câu lệnh đầu tạo **một mảng gồm hai reference slot**, không tạo hai đối tượng `Person`.

Muốn có instance thực, cần cấp phát riêng:

```java
people[0] = new Person("Lan");
people[1] = new Person("Minh");
```

Tạo `Person[]` không tự kích hoạt class initialization của `Person` chỉ vì class đó là component type. Class có thể được load/resolve theo runtime, nhưng **class loading khác class initialization**, và việc instantiate một `Person` thực mới là một tình huống kích hoạt initialization khi cần.

### 5.3. Component type của `anewarray` có thể là array type

Ví dụ quan trọng:

```java
int[][] rows = new int[2][];
```

Ở đây **component type trực tiếp** của outer array là **`int[]`**, tức một **reference type**. Vì vậy compiler thường dùng `anewarray` tham chiếu constant-pool array class descriptor `[I`:

```text
iconst_2
anewarray #k   // class "[I" tức int[]
```

Kết quả:

```text
rows --> [null, null]         // outer array length = 2
           |     |
           X     X            // chưa có mảng hàng
```

Việc kiểu nguồn là `int[][]` **không có nghĩa tự động dùng `multianewarray`**; biểu thức chỉ cấp phát một tầng mảng.

Nếu sau đó:

```java
rows[0] = new int[] {1, 2};
rows[1] = new int[] {3};
```

thì hai mảng `int[]` mới được tạo riêng, thông thường bằng `newarray int`. Cách này còn tạo ra các **jagged arrays** có độ dài hàng khác nhau.

## 6. `multianewarray` — cấp phát nhiều chiều

### 6.1. Định dạng instruction

```text
multianewarray indexbyte1 indexbyte2 dimensions
```

- Opcode byte: **`0xc5`**.
- Hai byte tiếp theo chỉ tới constant-pool entry mô tả **array type**, ví dụ `[[I`.
- Byte `dimensions` quy định **số chiều được cấp phát** trong instruction.
- Operand stack phải chứa đúng **số lượng giá trị `int` tương ứng với `dimensions`**.
- Khi thành công, JVM cấp phát outer array và những mảng con thuộc các chiều được yêu cầu, rồi đẩy **reference tới outer array** lên stack.

Điều kiện hợp lệ: `dimensions` ít nhất **1** và không được vượt **số chiều/rank của array type**. `multianewarray` hỗ trợ cả việc cấp phát một phần số chiều, không buộc số chiều cấp phát bằng tổng rank.

### 6.2. Hai chiều primitive: `new int[2][3]`

```java
int[][] matrix = new int[2][3];
```

Bytecode thông thường:

```text
iconst_2
iconst_3
multianewarray #k, 2  // class "[[I"
```

Mô phỏng operand stack, đỉnh ở bên phải:

```text
[]        → [2]       → [2, 3]
                            |
                            | multianewarray [[I, dimensions=2
                            v
                       [ref(matrix)]
```

Object graph:

```text
matrix --> [ref hàng 0, ref hàng 1]      outer int[][] length 2
                |           |
                v           v
             [0,0,0]     [0,0,0]         mỗi int[] length 3
```

Có **ba array objects**: một outer array và hai mảng con. Đây là **mảng của các mảng**, không phải cam kết phải có một khối bộ nhớ primitive liên tục như ma trận native.

### 6.3. Hai chiều reference: `new String[2][3]`

```java
String[][] labels = new String[2][3];
```

Biểu thức thường dùng `multianewarray` với array type descriptor `[[Ljava/lang/String;` và `dimensions = 2`. Sau cấp phát:

- Outer array có length 2, các phần tử trỏ tới hai `String[]` đã cấp phát.
- Mỗi hàng `String[]` có length 3.
- Sáu slot `String` reference ban đầu đều **null**.
- **Không có sáu instance `String` được tự động tạo ra**.

### 6.4. Chỉ cấp phát một phần tổng rank

```java
int[][][] cube = new int[2][3][];
```

**Rank là 3** nhưng **số chiều được cấp phát là 2**. `javac` có thể dùng:

```text
iconst_2
iconst_3
multianewarray #k, 2  // class "[[[I"
```

Object graph ban đầu:

```text
cube  (length 2)
  ├── cube[0] (length 3) : [null, null, null]
  └── cube[1] (length 3) : [null, null, null]
```

Đã tạo **ba array objects**, nhưng **chưa có object `int[]` nào** ở cấp cuối. Các slot tham chiếu tới hàng sâu hơn còn null. Sau đó có thể làm:

```java
cube[0][0] = new int[] {7, 8};
```

Điểm then chốt: operand `dimensions` = 2 **không nói rằng outer array dài 2**. Nó nói có **hai chiều được cấp phát**. Độ dài 2 và 3 trong ví dụ đã nằm trên **operand stack**.

### 6.5. Không chỉ áp dụng cho primitive

`multianewarray` dùng được với array types nhiều chiều có component primitive hoặc reference:

```java
int[][] a = new int[2][3];
Object[][] b = new Object[2][3];
String[][][] c = new String[2][3][4];
```

Trong trường hợp reference component, các phần tử cuối cùng ban đầu null; trong trường hợp primitive component, chúng giữ default value của primitive.

## 7. Bảng so sánh chính xác

| Thuộc tính | `new` | `newarray` | `anewarray` | `multianewarray` |
| --- | --- | --- | --- | --- |
| Opcode byte | `0xbb` | `0xbc` | `0xbd` | `0xc5` |
| Operand bytes | 2-byte constant-pool index | 1-byte `atype` | 2-byte constant-pool index | 2-byte index + 1-byte dimensions |
| Input trên operand stack | Không có array length | Một `int` count | Một `int` count | `dimensions` giá trị `int` count |
| Object cấp phát | Instance class | Primitive array | Reference array | Outer array và các tầng mảng chỉ định |
| Giá trị đẩy lên stack | Uninitialized reference theo verifier | Array reference | Array reference | Outer-array reference |
| Giá trị ban đầu của phần tử | Default fields, rồi constructor hoàn tất | Primitive defaults | Null reference | Defaults và references tới các subarrays |
| Cần `<init>` cho kết quả? | Theo mô hình tạo instance thông thường: có | Không | Không | Không |

**Lưu ý về chữ “uninitialized”:** sau `new` object đã được cấp phát. Trạng thái `uninitialized reference` là trạng thái **xác minh bytecode** để bảo đảm constructor được gọi hợp lệ, không có nghĩa object hoàn toàn không có bộ nhớ.

**Lưu ý về số chiều:** `newarray` tạo mảng primitive một tầng; `anewarray` tạo một tầng có component là reference, nên tầng đó vẫn có thể mang kiểu `int[][]`. `multianewarray` có thể cấp phát nhiều tầng trong một instruction.

## 8. Ví dụ Java 17+ hoàn chỉnh có stdout xác định

Tạo tệp **`AllocationDemo.java`** với nội dung:

```java
import java.util.Arrays;

public final class AllocationDemo {
    static final class Item {
        static {
            System.out.println("Item class initialized");
        }

        final String name;

        Item(String name) {
            this.name = name;
            System.out.println("Item constructor: " + name);
        }
    }

    public static void main(String[] args) {
        Item[] slots = new Item[2];
        System.out.println("item-array first is null: " + (slots[0] == null));

        Item item = new Item("Lan");
        slots[0] = item;
        System.out.println("instance: " + slots[0].name);

        int[] counts = new int[3];
        counts[1] = 7;
        System.out.println("primitive array: " + Arrays.toString(counts));

        String[] labels = new String[2];
        labels[0] = "JVM";
        System.out.println("reference array: " + Arrays.toString(labels));

        int[][] matrix = new int[2][3];
        matrix[1][2] = 9;
        System.out.println("matrix: " + Arrays.deepToString(matrix));

        int[][] jagged = new int[2][];
        jagged[0] = new int[] {4};
        System.out.println("partial second row null: " + (jagged[1] == null));
        System.out.println("jagged first row: " + Arrays.toString(jagged[0]));

        System.out.println("int component: "
                + counts.getClass().getComponentType().getName());

        try {
            int[] impossible = new int[-1];
            System.out.println(impossible.length);
        } catch (NegativeArraySizeException ex) {
            System.out.println("negative length rejected");
        }
    }
}
```

Biên dịch và chạy bằng **JDK 17 trở lên**:

```bash
javac --release 17 AllocationDemo.java
java AllocationDemo
```

**Stdout chính xác trong lần chạy thông thường:**

```text
item-array first is null: true
Item class initialized
Item constructor: Lan
instance: Lan
primitive array: [0, 7, 0]
reference array: [JVM, null]
matrix: [[0, 0, 0], [0, 0, 9]]
partial second row null: true
jagged first row: [4]
int component: int
negative length rejected
```

### Phân tích output

**`item-array first is null: true`:** `new Item[2]` tạo một array object, chưa tạo instance `Item` và chưa tự kích hoạt class initialization của `Item` vì đây chỉ là component type của mảng.

**`Item class initialized`:** xuất hiện khi chương trình lần đầu active-use `Item` bằng biểu thức `new Item("Lan")`, khiến class được khởi tạo khi cần.

**`Item constructor: Lan`:** xuất hiện khi constructor của instance chạy. Điều này cho thấy khác biệt giữa **class initialization** và **instance initialization**.

**`primitive array: [0, 7, 0]`:** `newarray int` tạo ba phần tử zero trước khi code gán `counts[1] = 7`.

**`reference array: [JVM, null]`:** `anewarray` tạo các reference slots ban đầu null. Sau đó `labels[0]` trỏ tới một `String`.

**`matrix: [[0, 0, 0], [0, 0, 9]]`:** outer array và hai mảng hàng đã được cấp phát; code thay đúng một phần tử primitive.

**`partial second row null: true`:** `new int[2][]` chưa cấp phát mảng hàng thứ hai.

**`int component: int`:** `getComponentType()` phản ánh primitive component type thực tế của `counts`; bản thân `counts` vẫn là reference tới một array object.

**`negative length rejected`:** độ dài âm hợp lệ ở biểu thức số nhưng không hợp lệ để cấp phát mảng, nên phát sinh `NegativeArraySizeException` và được bắt trong chương trình.

## 9. Dùng `javap` để nhìn opcode thực

Sau khi biên dịch:

```bash
javap -c -p AllocationDemo
```

Cờ `-c` in bytecode của phương thức, `-p` hiển thị thêm các thành viên không public. Muốn xem constant pool, descriptor và `Code` attribute:

```bash
javap -v -p AllocationDemo
```

Tìm trong `main` những nhóm instruction phù hợp:

| Mã nguồn | Opcode nên tìm |
| --- | --- |
| `new Item[2]` | `anewarray`, component type `AllocationDemo$Item` |
| `new Item("Lan")` | `new`, `dup`, `invokespecial <init>` |
| `new int[3]` | `newarray int` |
| `new String[2]` | `anewarray java/lang/String` |
| `new int[2][3]` | `multianewarray [[I, 2` |
| `new int[2][]` | `anewarray [I` |
| `new int[] {4}` | `newarray int` rồi lưu phần tử |
| `new int[-1]` | nạp giá trị `-1` rồi `newarray int` |

Các dạng trong bảng là **tên instruction/dữ liệu dự kiến**, không phải trích nguyên văn output với constant-pool index hoặc bytecode offset cố định. Compiler/JDK cụ thể quyết định những chỉ số và cách bố trí lệnh.

### Đọc output `new` và `anewarray`

Ví dụ minh họa:

```text
new        #k    // class AllocationDemo$Item
anewarray  #m    // class java/lang/String
```

`#k` và `#m` là **constant-pool indexes**. Hai operand bytes sau opcode dùng để truy cập thông tin class, không phải địa chỉ heap hay số phần tử.

Trong cả hai trường hợp, operand bytes không trực tiếp lưu độ dài mảng; `new` không cần count, còn `anewarray` lấy count từ operand stack.

### Đọc output `multianewarray`

```text
multianewarray #k, 2  // class "[[I"
```

- `#k` xác định kiểu array `int[][]`, descriptor `[[I`.
- Số **2** sau dấu phẩy cho biết **số chiều cần cấp phát**, không phải chiều dài mảng.
- Hai kích thước thật **2** và **3** của `new int[2][3]` đã được đẩy lên operand stack trước instruction.
- Instruction trả **một reference đến outer array**.

Nếu mã nguồn là `new int[4][7]`, operand `dimensions` vẫn là **2**, mặc dù độ dài thực là 4 và 7.

## 10. Đọc descriptor để hiểu component type và rank

Descriptor của JVM là cách mã hóa **kiểu**, không bao gồm độ dài mảng:

| Java type | JVM descriptor |
| --- | --- |
| `int` | `I` |
| `boolean` | `Z` |
| `java.lang.String` | `Ljava/lang/String;` |
| `int[]` | `[I` |
| `int[][]` | `[[I` |
| `int[][][]` | `[[[I` |
| `String[]` | `[Ljava/lang/String;` |
| `String[][]` | `[[Ljava/lang/String;` |

Mỗi ký tự `[` thêm một chiều. Tuy nhiên, **component type trực tiếp** của `int[][]` là `int[]` chứ không phải `int`. Do đó:

```java
int[][] rows = new int[2][];
```

có thể dùng `anewarray` vì phần tử outer array thuộc kiểu reference `int[]`. Ngược lại:

```java
int[] row = new int[2];
```

dùng `newarray` vì phần tử trực tiếp là primitive `int`.

Phân biệt:

- **Rank:** tổng số chiều của array type.
- **Allocated dimensions:** số chiều thực sự được cấp phát trong lần chạy.
- **Component type:** kiểu phần tử **trực tiếp** của một array object.
- **Length:** số phần tử thực tế của một array object khi chạy.

Ví dụ `new int[2][3][]` có rank 3, hai allocated dimensions, outer array length 2 và component type trực tiếp của outer array là `int[][]`.

## 11. Mảng có độ dài 0, âm và quá lớn

### Độ dài 0 vẫn tạo một array object hợp lệ

```java
int[] a = new int[0];
String[] b = new String[0];
int[][] c = new int[0][5];
```

Các reference `a`, `b`, `c` đều **khác null** khi cấp phát thành công. `c` là outer array độ dài 0; không có hàng nào để truy cập `c[0]`. Mảng độ dài 0 khác với một reference bằng null.

### Độ dài âm: `NegativeArraySizeException`

```java
// Biểu thức hợp lệ về cú pháp, sẽ ném khi thực thi:
int[] bad = new int[-1];
```

`newarray`, `anewarray` và `multianewarray` kiểm tra các count mảng theo ngữ nghĩa của từng opcode. Nếu một kích thước cần cấp phát là âm, chương trình không được cấp phát thành công rồi trả về một mảng “nửa hoàn tất” làm kết quả.

### Bộ nhớ không vô hạn

Có thể gặp `OutOfMemoryError` ngay cả khi mọi count đều không âm, do giới hạn heap, số object cần tạo, giới hạn kích thước mảng hoặc tài nguyên hệ thống.

`new int[10000][10000]` có thể cần một outer array và **10.000 inner arrays**, không phải chỉ một mảng đơn. Những cấu trúc thưa có thể dùng `new int[10000][]` để cấp phát hàng khi thật sự cần, nhưng phải xử lý các hàng chưa có object và đang null.

Không đoán một giới hạn độ dài mảng chung cho mọi JVM chỉ dựa vào khả năng biểu diễn của `int`; triển khai và cấu hình runtime còn ảnh hưởng.

## 12. Các opcode đọc/ghi mảng sau cấp phát

Các opcode cấp phát chỉ tạo đối tượng. Đọc/ghi phần tử sử dụng các lệnh khác:

| Mảng/thao tác | Opcode |
| --- | --- |
| `int[]` đọc/ghi phần tử | `iaload` / `iastore` |
| `long[]` đọc/ghi phần tử | `laload` / `lastore` |
| `double[]` đọc/ghi phần tử | `daload` / `dastore` |
| Mảng reference đọc/ghi phần tử | `aaload` / `aastore` |
| Lấy độ dài array | `arraylength` |

Xét:

```java
int[][] rows = new int[2][];
rows[0] = new int[] {5};
rows[0][0] = 7;
```

- `rows[0] = ...` ghi **reference `int[]`** vào outer array, nên dùng nhóm **`aastore`**.
- `rows[0][0] = 7` ghi **primitive int** vào inner array, nên dùng nhóm **`iastore`**.

Ngay cả mảng của mảng primitive cũng là mảng reference ở tầng ngoài. Không quyết định opcode store chỉ dựa trên primitive sâu nhất.

Với mảng reference có covariance:

```java
Object[] values = new String[1];
// values[0] = Integer.valueOf(7); // biên dịch được, nhưng ArrayStoreException
```

Phần tử của `String[]` ở runtime chỉ có thể giữ reference phù hợp với `String` (hoặc null), nên phép ghi qua một biến `Object[]` không loại bỏ kiểm tra kiểu runtime.

## 13. Vì sao object arrays không tự initialize class component?

Cần phân biệt:

1. **Class loading/resolution:** runtime biết thông tin/định nghĩa kiểu cần sử dụng.
2. **Class initialization:** chạy các static initializers của class khi active use yêu cầu.
3. **Instance initialization:** constructor tạo trạng thái của object sau `new`.
4. **Array initialization:** thiết lập giá trị mặc định cho các slot của array object.

`new Widget[2]` chỉ yêu cầu **một mảng có component type `Widget`**, không khởi tạo hai `Widget`. Nó không tự gọi constructor và không tự kích hoạt class initialization của `Widget` chỉ vì component type.

`new Widget()` khác: đây là active use khiến class cần được khởi tạo nếu chưa, rồi chạy constructor thích hợp.

Lưu ý: class/array resolution có thể gây các hoạt động tải lớp hoặc lỗi liên kết, nhưng không nên đánh đồng việc **biết một kiểu** với việc **chạy static initializer** của kiểu đó.

## 14. Các lỗi hiểu sai thường gặp

1. **“Nguồn có `new` thì bytecode chỉ có opcode `new`.”** Sai. Mảng dùng opcode khác.
2. **“`newarray` tạo được `String[]` với một `atype` nào đó.”** Sai. `atype` chỉ dành cho tám primitive types.
3. **“`anewarray` tạo sẵn mọi object phần tử.”** Sai. Các phần tử reference ban đầu null.
4. **“`new int[2][]` tạo hai hàng rỗng.”** Sai. Nó tạo outer array; các hàng là null.
5. **“`multianewarray #k, 2` có nghĩa outer array dài 2.”** Sai. Số 2 cuối là allocated dimensions.
6. **“`int[][]` luôn được tạo bằng `multianewarray`.”** Sai. `new int[2][]` thường dùng `anewarray`.
7. **“`new` đã gọi constructor, `invokespecial` là thừa.”** Sai. Hai instruction phục vụ hai giai đoạn riêng.
8. **“Primitive array không phải object.”** Sai. `int[]` là một reference type và một array object.
9. **“`new int[0]` tương đương null.”** Sai. Đó là array object có length 0.
10. **“Các count của mảng nằm bên trong operand bytes của `anewarray`.”** Sai. Count ở operand stack; operand bytes của `anewarray` chứa constant-pool index.
11. **“`multianewarray` cấp phát đủ mọi chiều trong descriptor.”** Sai. Nó chỉ cấp phát `dimensions` chiều được chỉ định.
12. **“Array 2D luôn là một khối primitive liên tục.”** Sai theo mô hình Java: mảng của các mảng.
13. **“Mảng của interface không hợp lệ.”** Sai. `Runnable[]` hợp lệ dù không instantiate trực tiếp `Runnable`.
14. **“Một object được cấp phát nghĩa là toàn bộ quá trình khởi tạo instance đã thành công.”** Sai. Constructor có thể ném exception.
15. **“Có lỗi khi cấp phát mảng thì phải là `VerifyError`.”** Sai. Kích thước âm hoặc thiếu bộ nhớ là những lỗi runtime riêng; verifier xử lý ràng buộc class file và kiểu.

## 15. Bài tập tự kiểm tra kèm đáp án

**Câu 1.** `new long[4]` dùng opcode nào, tạo bao nhiêu object?

**Đáp án:** `newarray` với `atype = T_LONG (11)`; tạo **một mảng `long[]`** gồm bốn phần tử `0L`.

**Câu 2.** `new Runnable[3]` có hợp lệ khi `Runnable` là interface không?

**Đáp án:** Có. `anewarray` tạo một array object với ba null reference slots; nó không tạo instance `Runnable`.

**Câu 3.** `new int[2][3]` thường tạo bao nhiêu array objects?

**Đáp án:** Một outer array và hai `int[]` rows, tổng **ba objects**, thường bằng `multianewarray` với `dimensions=2`.

**Câu 4.** `new int[2][]` tạo bao nhiêu object lúc đầu?

**Đáp án:** **Một outer array**, hai phần tử reference bằng null; compiler thường dùng `anewarray` với component type `int[]`.

**Câu 5.** `multianewarray [[I, 2`: số 2 cuối là gì?

**Đáp án:** Số chiều được cấp phát. Các kích thước thật nằm trên operand stack.

**Câu 6.** `new Person[5]` có chạy constructor `Person()` năm lần không?

**Đáp án:** Không. Mảng chứa năm null references ban đầu.

**Câu 7.** Vì sao `new Person("Lan")` thường đi kèm `dup` và `invokespecial <init>`?

**Đáp án:** `new` cấp phát; `dup` giữ reference làm kết quả biểu thức; `invokespecial` gọi constructor, tiêu thụ receiver/arguments theo quy tắc.

**Câu 8.** `new String[0]` có bằng null không?

**Đáp án:** Không. Nó là object mảng hợp lệ với `length == 0`.

**Câu 9.** Gán `rows[0] = new int[3]` trên `int[][] rows` dùng store primitive hay reference ở mảng ngoài?

**Đáp án:** **Reference store `aastore`**, vì phần tử trực tiếp của `int[][]` là `int[]`.

**Câu 10.** Với `new int[2][3][]`, những chiều nào đã có mảng?

**Đáp án:** Hai chiều đầu. Các reference cần trỏ tới `int[]` ở cấp cuối ban đầu là null.

**Câu 11.** `new int[-1]` sai ở compile-time hay runtime?

**Đáp án:** Biểu thức đúng kiểu; runtime ném `NegativeArraySizeException` khi cấp phát.

**Câu 12.** Tại sao `new int[10000][10000]` có nguy cơ hết heap nhanh?

**Đáp án:** Vì có rất nhiều phần tử và nhiều object mảng con, không chỉ một object; tổng bộ nhớ có thể vượt giới hạn JVM.

## 16. Tổng kết bằng một sơ đồ

```text
new
  operand bytes: constant-pool index cho class
  stack input  : không có độ dài mảng
  stack output : reference tới instance chưa hoàn tất <init>
  ví dụ        : new Person("Lan")

newarray
  operand bytes: atype = một trong tám primitive types
  stack input  : một int count
  stack output : reference tới primitive array
  ví dụ        : new int[3]

anewarray
  operand bytes: constant-pool index cho component reference type
  stack input  : một int count
  stack output : reference tới reference array
  ví dụ        : new String[3], new int[2][]

multianewarray
  operand bytes: constant-pool array type index + dimensions
  stack input  : dimensions giá trị int count
  stack output : reference tới outer array
  ví dụ        : new int[2][3], new String[2][3], new int[2][3][]
```

**Ghi nhớ:** Từ khóa `new` ở mã nguồn là một ý tưởng; các opcode `new`, `newarray`, `anewarray` và `multianewarray` diễn đạt **những hình thức cấp phát khác nhau** ở cấp JVM. Khi đọc `javap`, hãy hỏi: **kiểu được chỉ định ở đâu, số kích thước lấy từ đâu, tạo bao nhiêu array objects và phần tử ban đầu là gì?**

### Tài liệu đặc tả chính thức

- [JVMS 6.5 — new](https://docs.oracle.com/javase/specs/jvms/se21/html/jvms-6.html#jvms-6.5.new): cấp phát instance của class.
- [JVMS 6.5 — newarray](https://docs.oracle.com/javase/specs/jvms/se21/html/jvms-6.html#jvms-6.5.newarray): `atype` và primitive arrays.
- [JVMS 6.5 — anewarray](https://docs.oracle.com/javase/specs/jvms/se21/html/jvms-6.html#jvms-6.5.anewarray): reference arrays.
- [JVMS 6.5 — multianewarray](https://docs.oracle.com/javase/specs/jvms/se21/html/jvms-6.html#jvms-6.5.multianewarray): dimensions và cấp phát mảng nhiều chiều.
- [JVMS 4.3 — Descriptors](https://docs.oracle.com/javase/specs/jvms/se21/html/jvms-4.html#jvms-4.3): ký hiệu kiểu class, primitive và array.
- [JLS 10 — Arrays](https://docs.oracle.com/javase/specs/jls/se21/html/jls-10.html): khái niệm, kiểu thành phần và phần tử mảng.
- [`javap` JDK 21](https://docs.oracle.com/en/java/javase/21/docs/specs/man/javap.html): đọc bytecode/constant pool.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="bytecode-instructions-008">getfield, putfield, getstatic và putstatic khi truy cập trường</a>

<details>
<summary>Xem chi tiết</summary>

# getfield, putfield, getstatic và putstatic khi truy cập trường trong JVM

## Mục tiêu bài học

Khi đọc mã nguồn Java, các biểu thức như `object.count`, `object.count = 5`, `Counter.total` hay `Counter.total = 10` trông gần giống nhau. Ở cấp bytecode JVM, chúng thuộc **hai nhóm truy cập khác nhau**: trường gắn với một đối tượng (*instance field*) và trường của lớp (*static field*). Ngoài ra, thao tác đọc và ghi cũng dùng opcode khác nhau.

Sau bài này, bạn sẽ:
- Phân biệt chính xác bốn instruction **`getfield`, `putfield`, `getstatic`, `putstatic`** theo đối tượng hay lớp, đọc hay ghi.
- Giải thích operand stack trước/sau mỗi lệnh; hiểu vì sao `getfield`/`putfield` cần `objectref`, còn các lệnh static không cần.
- Lần theo operand chỉ mục của bytecode đến **`CONSTANT_Fieldref`** và ý nghĩa của owner, field name, descriptor, resolution.
- Nhận biết tác động của `getstatic`/`putstatic` lên **class initialization**, quy định đối với `final`, `volatile` và các lỗi runtime.
- Biên dịch ví dụ Java 17+, dùng **`javap -c -v -p`** để nhận diện và giải thích bytecode trong constructor, class initializer và phương thức thường.
- Tránh nhầm field hiding với method overriding, truy cập field với local variable, và constant inlining với đọc field runtime.

## 1. Bảng phân biệt bốn opcode

| Opcode | Mã opcode JVM | Loại field | Hành động | Thao tác với operand stack |
| --- | --- | --- | --- | --- |
| `getfield` | 180 (`0xb4`) | Instance | Đọc giá trị field từ đối tượng | `..., objectref → ..., value` |
| `putfield` | 181 (`0xb5`) | Instance | Ghi giá trị vào field của đối tượng | `..., objectref, value → ...` |
| `getstatic` | 178 (`0xb2`) | Static | Đọc giá trị field của lớp/interface khai báo | `... → ..., value` |
| `putstatic` | 179 (`0xb3`) | Static | Ghi giá trị vào field của lớp/interface khai báo | `..., value → ...` |

Đây là **mô hình của operand stack**, trong đó dấu `...` biểu diễn những phần tử thấp hơn không bị lệnh thay đổi. Stack được viết từ đáy (trái) đến đỉnh (phải).

- **`getfield`** cần tham chiếu đối tượng để biết instance nào đang được đọc; nó pop `objectref` rồi push giá trị field.
- **`putfield`** cần cả tham chiếu đối tượng và giá trị mới, với **`value` ở đỉnh**, `objectref` ngay dưới nó; lệnh pop cả hai và không để lại một giá trị trên stack.
- **`getstatic`** không cần objectref; field static gắn với class/interface đã được resolve.
- **`putstatic`** chỉ cần giá trị mới trên stack.

Tất cả bốn lệnh có cùng định dạng bytecode gồm một opcode và **hai byte operand chỉ mục**:

~~~text
getfield    indexbyte1 indexbyte2
putfield    indexbyte1 indexbyte2
getstatic   indexbyte1 indexbyte2
putstatic   indexbyte1 indexbyte2

constant-pool index = (indexbyte1 << 8) | indexbyte2
~~~

Toán hạng đó là chỉ mục không dấu **16-bit** trong *run-time constant pool* của class đang thực thi. Nó **không phải offset trực tiếp của field trong bộ nhớ**, cũng không phải địa chỉ của một object. Khi xem bằng `javap`, nó được in dưới dạng `#N` kèm chú thích về trường được tham chiếu.

## 2. Hai kiểu trường: instance và static

Xét định nghĩa class:

~~~java
public class Counter {
    int count;           // Instance field
    static int total;    // Static field
}
~~~

Đây là một **class minh họa khai báo**; chưa có phương thức `main` để chạy. Ý nghĩa:

- Mỗi đối tượng `new Counter()` có trạng thái `count` của **riêng nó**. Đọc `a.count` và `b.count` có thể trả giá trị khác nhau.
- Trường `total` thuộc về **class `Counter`** theo ngữ nghĩa Java. Mọi instance cùng định nghĩa class đó truy cập cùng trường static.
- Instance field và static field là hai loại symbol truy cập riêng; một opcode `getfield` **không được dùng để đọc trường static**, và `getstatic` **không được dùng để đọc instance field**.
- Kiểu Java của field (`int`, `long`, `String`, ...) không quyết định việc dùng `getfield` hay `getstatic`; **tính static** mới là điểm phân biệt.

Ví dụ mã nguồn:

~~~java
Counter a = new Counter();
Counter b = new Counter();
a.count = 3;
b.count = 8;
Counter.total = 11;
~~~

Các phép gán cho `a.count` và `b.count` thường biên dịch thành **`putfield`** với objectref khác nhau; phép gán cho `Counter.total` thường dùng **`putstatic`**. Đoạn này minh họa biểu thức, muốn chạy phải đặt trong class/phương thức hợp lệ.

### Local variables, array elements và thuộc tính đối tượng khác gì?

| Mã nguồn / tác vụ | Nhóm bytecode phổ biến |
| --- | --- |
| Đọc một biến local `int n` | `iload`, `iload_0`... |
| Ghi một biến local `int n` | `istore`, `istore_0`... |
| Đọc/ghi instance field | `getfield` / `putfield` |
| Đọc/ghi static field | `getstatic` / `putstatic` |
| Lấy độ dài mảng `array.length` | `arraylength` |
| Đọc/ghi phần tử mảng `arr[i]` | `iaload/iastore`, `aaload/aastore` và biến thể khác tùy kiểu |

Mảng Java có thuộc tính `length` đặc biệt nhưng **không đọc bằng `getfield`**. Toán tử truy cập mảng được triển khai qua bộ lệnh mảng. JavaBean getter như `obj.getCount()` là **lời gọi phương thức** (chẳng hạn `invokevirtual`), không tự động là `getfield` đối với caller. Một field tên `count` và method tên `getCount` là hai thành viên khác nhau.

## 3. Operand stack trong lệnh truy cập instance field

### `getfield`: pop tham chiếu, push giá trị

Giả sử `counter` có `count = 7`. Mã nguồn:

~~~java
int result = counter.count;
~~~

Một hình thức bytecode khái niệm:

~~~text
aload_1              // stack: [..., objectref]
getfield #12         // stack: [..., 7]
istore_2             // stack: [...]
~~~

Giá trị `objectref` đến từ `aload_1` (giả định biến `counter` nằm tại local slot 1); chỉ số `#12` minh họa và **không phải chỉ số cố định**. Trước `getfield`, JVM phải có đúng tham chiếu của instance thuộc loại phù hợp trên đỉnh stack. Sau `getfield`, tham chiếu ấy bị tiêu thụ, chỉ còn giá trị field đã đọc.

Nếu instance field có kiểu `long` hay `double`, giá trị trả lên stack thuộc **category 2** (chiếm hai đơn vị độ sâu); kiểu `int`, `float` và reference là category 1. Với field mang descriptor `Z`, `B`, `C`, `S`, giá trị stack dùng dạng **`int`** theo quy tắc JVM; điều này không đồng nghĩa toàn bộ field trong object đều có 32 bit lưu trữ vật lý.

### `putfield`: thứ tự stack bắt buộc

Với câu lệnh:

~~~java
counter.count = 9;
~~~

Bytecode dạng thường gặp:

~~~text
aload_1              // stack: [..., objectref]
bipush 9             // stack: [..., objectref, 9]
putfield #12         // stack: [...]
~~~

**Objectref nằm dưới value**. Trật tự ngược lại sẽ không thỏa stack contract của `putfield`. Khi biên dịch biểu thức gán **có sử dụng giá trị kết quả**, compiler có thể dùng instruction như `dup`/`dup_x1`/`dup2_x1` để giữ lại một bản giá trị trước khi `putfield` tiêu thụ operands. `putfield` tự nó **không push kết quả assignment**.

Câu lệnh `counter.count++` thường gồm các bước đọc rồi ghi:

~~~text
aload_1
dup
getfield #12
iconst_1
iadd
putfield #12
~~~

Đây là mô hình minh họa thao tác tăng trên **instance field int**. Opcode `iinc` chỉ thao tác trực tiếp local variable kiểu int, **không** thay thế toàn bộ `getfield` + `iadd` + `putfield`. Với kiểu nhỏ hơn int, compiler có thể cần narrowing như `i2b`, `i2s` hay `i2c`.

**Tăng field bằng chuỗi lệnh không có nghĩa là atomic.** Nếu nhiều thread cùng thực hiện `counter.count++` không đồng bộ, chúng có thể đọc cùng giá trị, tính rồi ghi đè nhau; `volatile` đơn thuần cũng không biến phép read-modify-write thành atomic (xem mục đồng thời).

## 4. Truy cập static field và khởi tạo class

### `getstatic`

Đọc một static field:

~~~java
int x = Counter.total;
~~~

Bytecode minh họa:

~~~text
getstatic #18         // stack: [..., total]
istore_1              // stack: [...]
~~~

Không cần `aload` một đối tượng `Counter` trước `getstatic`. JVM resolve reference đến field và nếu **class hoặc interface thực sự khai báo field đó** chưa được khởi tạo, `getstatic` kích hoạt việc khởi tạo theo quy tắc JVMS (`5.5), ngoại trừ trường hợp code đã được biên dịch theo hình thức khác như inline compile-time constant.

### `putstatic`

Ghi static field:

~~~java
Counter.total = 20;
~~~

Bytecode thường gặp:

~~~text
bipush 20             // stack: [..., 20]
putstatic #18         // stack: [...]
~~~

Khi thực thi thật, `putstatic` cũng yêu cầu class/interface **khai báo field được resolve** hoàn thành initialization nếu chưa xong, trước khi ghi giá trị mới theo quy tắc JVM.

### Vì sao class initialization quan trọng?

Trong giai đoạn **preparation**, các static field (không kể một số gán hằng theo `ConstantValue`) được chuẩn bị với **giá trị mặc định** theo kiểu: 0 cho `int`, `false` cho `boolean`, `null` cho reference. Giai đoạn **initialization** chạy class initializer `<clinit>` nếu có, thực thi các static initializer/biểu thức khởi tạo theo thứ tự do Java quy định.

Khi class đã được khởi tạo thành công, đọc `getstatic` trả giá trị hiện tại của field. Nếu initialization thất bại, truy cập có thể dẫn tới `ExceptionInInitializerError` hoặc ở những lần dùng tiếp theo là `NoClassDefFoundError`, tùy trường hợp.

Tránh khái quát “lần đầu nhắc tên class sẽ khởi tạo class”: class literal `SomeClass.class`, hằng compile-time đã inline và nhiều thao tác reflection/class loading có quy tắc riêng. Nói chính xác **đọc/ghi static field được resolve qua opcode getstatic/putstatic** là một trong những hoạt động kích hoạt class initialization.

## 5. Ví dụ Java 17+ dùng đủ bốn opcode

Tạo đúng tệp **`FieldOpsDemo.java`**:

~~~java
public class FieldOpsDemo {
    static int total = 10;
    int value;

    FieldOpsDemo(int initial) {
        value = initial;
    }

    static int change(FieldOpsDemo target) {
        int current = target.value;
        target.value = current + 2;

        int previousTotal = total;
        total = previousTotal + 1;

        return target.value + total;
    }

    public static void main(String[] args) {
        FieldOpsDemo box = new FieldOpsDemo(5);
        System.out.println("sum=" + change(box));
        System.out.println("value=" + box.value);
        System.out.println("total=" + total);
    }
}
~~~

Biên dịch và chạy trên **JDK 17 hoặc mới hơn**:

~~~bash
javac --release 17 FieldOpsDemo.java
java FieldOpsDemo
~~~

**Stdout chính xác:**

~~~text
sum=18
value=7
total=11
~~~

Phân tích:

1. Trước khi thực thi `main`, class `FieldOpsDemo` được initialization. Lời khai báo `static int total = 10;` thông thường làm compiler sinh một đoạn trong `<clinit>` có **`putstatic FieldOpsDemo.total:I`**.
2. Constructor nhận `initial = 5` và ghi `this.value = 5` bằng **`putfield FieldOpsDemo.value:I`**.
3. Trong `change`, `target.value` được đọc qua **`getfield`**, cộng 2, rồi được ghi lại bằng **`putfield`**.
4. `total` là static field nên việc đọc ra `previousTotal` dùng **`getstatic`**, còn phép gán `total = previousTotal + 1` dùng **`putstatic`**.
5. Biểu thức trả về đọc cả `target.value` qua **`getfield`** và `total` qua **`getstatic`**, tính `7 + 11 = 18`.

Quan sát bytecode và constant pool:

~~~bash
javap -c -p FieldOpsDemo
javap -v -p FieldOpsDemo
~~~

Tìm trong phương thức `change` các dòng có chú thích dạng:

~~~text
getfield     #N  // Field value:I
putfield     #N  // Field value:I
getstatic    #M  // Field total:I
putstatic    #M  // Field total:I
~~~

Phần trên là **mẫu để nhận biết**, không phải toàn bộ output của một bản build cụ thể. Chỉ số `#N` và `#M`, offset và lựa chọn một số instruction phụ thuộc vào compiler/phiên bản. Trong `main` còn có `getstatic java/lang/System.out:Ljava/io/PrintStream;` để lấy stream in ra console; đây là một ví dụ static field **thuộc class khác** với `FieldOpsDemo`.

### Theo dõi operand stack ở phương thức `change`

Giả sử local slot 0 chứa `target`:

~~~text
Bước đọc instance:
  aload_0               Stack [target]
  getfield value:I      Stack [5]
  istore_1              Stack []

Bước ghi instance:
  aload_0               Stack [target]
  iload_1               Stack [target, 5]
  iconst_2              Stack [target, 5, 2]
  iadd                  Stack [target, 7]
  putfield value:I      Stack []

Bước đọc static:
  getstatic total:I     Stack [10]
  istore_2              Stack []

Bước ghi static:
  iload_2               Stack [10]
  iconst_1              Stack [10, 1]
  iadd                  Stack [11]
  putstatic total:I     Stack []
~~~

Đây là **sơ đồ phân tích logic**; thứ tự opcode trong disassembly thực tế có thể xuất hiện với những dạng tương đương. Bất biến cốt lõi là giá trị trên stack phù hợp với hợp đồng lệnh và descriptor trường.

## 6. Fieldref trong constant pool: owner + name + descriptor

Bốn opcode không chứa tên field dưới dạng text ngay trong operand instruction. Chúng dùng chỉ mục **`CONSTANT_Fieldref`** trong run-time constant pool của class đang thực thi.

Một symbolic reference điển hình có chuỗi:

~~~text
getfield #12
    |
    +--> #12 CONSTANT_Fieldref
           |
           +--> class_index --------> CONSTANT_Class
           |                              |
           |                              +--> CONSTANT_Utf8 "org/example/Account"
           |
           +--> name_and_type_index -> CONSTANT_NameAndType
                                         |
                                         +--> CONSTANT_Utf8 "balance"
                                         +--> CONSTANT_Utf8 "J"
~~~

Theo đó, một tham chiếu symbolic có thể mang ý nghĩa:
**`org/example/Account.balance:J`**, tức field tên `balance` có descriptor `J` (`long`) thuộc class/interface được xác định qua quy tắc resolution.

Định dạng `CONSTANT_Fieldref_info` có tag 9 và hai chỉ mục `class_index`, `name_and_type_index`. `CONSTANT_NameAndType` lại trỏ đến hai `CONSTANT_Utf8`: **tên field** và **field descriptor**. Chỉ mục không thể hiểu đúng nếu chỉ nhìn một số `#12` mà không tra cả chuỗi entry.

Một vài field descriptor thường gặp:

| Descriptor | Kiểu Java |
| --- | --- |
| `I` | `int` |
| `J` | `long` |
| `D` | `double` |
| `F` | `float` |
| `Z` | `boolean` |
| `B` | `byte` |
| `S` | `short` |
| `C` | `char` |
| `Ljava/lang/String;` | `java.lang.String` |
| `[I` | `int[]` |
| `Ljava/util/List;` | `java.util.List` (erasure của kiểu generic như `List<String>`) |

**Descriptor không phải tên kiểu hiển thị dạng Java.** Ví dụ field `long balance` có descriptor `J`, không phải chuỗi `long`. Trong `javap -v`, có thể thấy dòng như `Fieldref ... // Field org/example/Account.balance:J`.

## 7. Resolution, kiểm tra loại field và kiểm tra quyền truy cập

Một JVM phải xác định field thực tế từ symbolic reference qua **field resolution** (JVMS `5.4.3.2), theo quy tắc tìm field trong class/interface hierarchy, kiểm tra class loading và khả năng truy cập. Quá trình resolution có thể thực hiện sớm hoặc theo nhu cầu, trong những giới hạn mà JVMS cho phép.

Phân biệt ba bước:

1. **Tham chiếu ký hiệu:** tên owner, tên field và descriptor đã có trong constant pool.
2. **Resolution:** JVM tìm field thực tế và thực hiện các kiểm tra liên kết/quyền theo quy tắc.
3. **Thực thi instruction:** kiểm tra loại static/instance, điều kiện `final` và objectref khi cần, rồi đọc/ghi giá trị.

**Không được đồng nhất `Fieldref` với địa chỉ/offset vật lý.** JVM triển khai có thể cache kết quả resolution hoặc tối ưu hóa đường truy cập khi chạy, nhưng thông tin nhị phân chuẩn hóa vẫn là symbolic reference.

### Field cùng tên nhưng khác loại/descriptor

Một lớp có thể công khai các member khác nhau qua nhiều phiên bản binary. Caller đã biên dịch theo field `score:I` sẽ tìm field phù hợp **tên và descriptor**, không đơn thuần là member cùng tên. Khi runtime cung cấp một phiên bản class mà field bị xóa hoặc đổi descriptor từ `I` sang `J`, liên kết có thể thất bại với `NoSuchFieldError`. Đổi field từ instance sang static (hoặc ngược lại) còn có thể làm instruction hiện có không tương thích với loại field, gây `IncompatibleClassChangeError`.

### Field hiding không phải dynamic dispatch

Trong Java, **field access được liên kết theo kiểu tham chiếu tại compile time**, khác virtual method dispatch. Xét:

~~~java
class Parent {
    int number = 1;
}

class Child extends Parent {
    int number = 2;
}
~~~

Nếu có:

~~~java
Parent p = new Child();
System.out.println(p.number);          // 1
System.out.println(((Child) p).number); // 2
~~~

Đây là đoạn minh họa cần đặt trong phương thức để chạy. `p.number` đã được compile để truy cập field của `Parent`; ép kiểu sang `Child` cho phép mã nguồn trỏ tới field khác của `Child`. JVM **không tự tìm “field override” theo class runtime của object** khi thực thi `getfield`, bởi field có thể bị *hide* chứ không *override*. Các phương thức instance mới là nơi thường có virtual dispatch qua opcode invocation phù hợp.

### Quyền truy cập được kiểm tra ở runtime

Khả năng compile được trong một cấu hình không có nghĩa binary luôn liên kết được sau khi thay JAR, module hoặc class loader. Các trường hợp thường gặp:
- Field không còn tồn tại: `NoSuchFieldError`.
- Field tồn tại nhưng không được phép truy cập: `IllegalAccessError`.
- Static/instance mismatch: `IncompatibleClassChangeError`.
- Owner class không thể nạp/được định nghĩa theo yêu cầu: có thể gặp `NoClassDefFoundError` hoặc lỗi liên kết liên quan.

Những lỗi này thuộc họ **`LinkageError`**, không giống ngoại lệ kiểm tra null ở bước thực thi `getfield`/`putfield`.

## 8. Truy cập null: vì sao chỉ instance field cần đối tượng?

Nếu `objectref` là `null` khi thực thi **`getfield` hoặc `putfield`** sau các kiểm tra liên kết liên quan, JVM ném `NullPointerException`.

Ví dụ Java 17+ đầy đủ:

Tạo **`NullFieldDemo.java`**:

~~~java
public class NullFieldDemo {
    int value = 5;
    static int shared = 9;

    public static void main(String[] args) {
        NullFieldDemo item = null;

        try {
            System.out.println(item.value);
        } catch (NullPointerException expected) {
            System.out.println("instance=NPE");
        }

        System.out.println("static=" + NullFieldDemo.shared);
    }
}
~~~

Chạy:

~~~bash
javac --release 17 NullFieldDemo.java
java NullFieldDemo
~~~

**Stdout chính xác:**

~~~text
instance=NPE
static=9
~~~

`item.value` đòi objectref và thất bại vì tham chiếu null. `NullFieldDemo.shared` sử dụng `getstatic`, không cần instance.

Java cho phép viết cú pháp static field qua biểu thức tham chiếu như `item.shared` (compiler có thể cảnh báo về phong cách), kể cả `item` đang null. Tuy nhiên, **biểu thức đứng trước dấu chấm vẫn có thể được đánh giá và gây side effect hoặc exception của chính nó**; vì vậy không nên viết static access qua biến object. Hãy dùng `TênLớp.field` để làm rõ ý nghĩa và tránh nhầm lẫn với `getfield`.

### Thứ tự lỗi và ngoại lệ

Trong trường hợp bytecode sai liên kết và objectref null đồng thời, đừng xây dựng logic dựa trên phỏng đoán “NPE nhất định được ném trước”. JVM phải tuân thủ thứ tự resolution/kiểm tra được đặc tả; lỗi liên kết có thể xảy ra trước bước kiểm tra objectref. Điều này đặc biệt quan trọng khi phân tích class file tự sinh hoặc binary lỗi tương thích.

## 9. `final`, hằng compile-time và các ngoại lệ ghi field

### Quy tắc `final` ở bytecode

Với định dạng class file Java hiện đại, verifier/linker hạn chế ghi field `final`:

- **`putfield` vào instance `final`**: field phải được khai báo trong **class hiện tại**, và instruction phải nằm trong **instance initialization method `<init>`** của chính class đó; nếu không, có thể xảy ra `IllegalAccessError` khi thực thi kiểm tra liên quan.
- **`putstatic` vào static `final`**: field phải được khai báo trong **class/interface hiện tại**, và instruction phải nằm trong **class/interface initialization method `<clinit>`**.
- `getfield`/`getstatic` có thể đọc field final từ vị trí được phép nếu resolution/access thành công.

Những quy tắc bytecode giải thích vì sao compiler Java chỉ cho phép gán `final` trong ngữ cảnh khởi tạo hợp lệ, nhưng **chúng không thay thế mọi quy tắc của JLS** về definite assignment, blank final và tính hợp lệ của constructor/source code.

### Không phải đọc `static final` luôn là `getstatic`

Một **constant variable** trong Java là field `final` kiểu primitive hoặc `String` được khởi tạo bằng *constant expression* phù hợp. Giá trị có thể được compiler **inline ngay tại nơi sử dụng**, nên code dùng field đó có thể **không chứa `getstatic`** cho field.

Ví dụ đầy đủ:

Tạo **`ConstantFieldDemo.java`**:

~~~java
class ConfigData {
    static final int LIMIT = 7; // Compile-time constant variable

    static int dynamic = start();

    private static int start() {
        System.out.println("ConfigData initialized");
        return 21;
    }
}

public class ConstantFieldDemo {
    public static void main(String[] args) {
        System.out.println("limit=" + ConfigData.LIMIT);
        System.out.println("before dynamic");
        System.out.println("dynamic=" + ConfigData.dynamic);
    }
}
~~~

Chạy:

~~~bash
javac --release 17 ConstantFieldDemo.java
java ConstantFieldDemo
~~~

**Stdout chính xác:**

~~~text
limit=7
before dynamic
ConfigData initialized
dynamic=21
~~~

Giải thích:

1. `ConfigData.LIMIT` là compile-time constant; compiler có thể đưa trực tiếp số 7 vào class caller. Bởi vậy phép đọc này không cần dùng `getstatic` và không kích hoạt khởi tạo `ConfigData` theo cách mà một `getstatic` thực sự làm.
2. `ConfigData.dynamic` không phải compile-time constant vì giá trị đến từ lời gọi `start()`. Khi bytecode thực hiện `getstatic` để đọc nó, JVM phải khởi tạo `ConfigData` trước.
3. Thân `<clinit>` chạy `start()` và in `ConfigData initialized`, rồi lưu giá trị 21 bằng `putstatic`.
4. Sau initialization, `getstatic` trả 21.

**Điểm quan trọng về binary compatibility:** Nếu một thư viện thay giá trị compile-time constant `static final int LIMIT` rồi người dùng **không biên dịch lại caller**, caller có thể tiếp tục sử dụng giá trị đã inline từ bản cũ. Đó là một lý do không nên dùng primitive/string compile-time constants để công bố những cấu hình cần thay đổi ở runtime giữa các bản phát hành.

### Final field không đồng nghĩa immutable object

`final Account account` ngăn gán lại tham chiếu field sau khi được khởi tạo hợp lệ, nhưng **không làm mọi field bên trong đối tượng Account trở thành bất biến**. Cũng không có bảo đảm thread-safety tự động cho mọi thao tác lên object được tham chiếu.

## 10. `volatile`, Java Memory Model và tính nguyên tử

Các field được đánh dấu `volatile` vẫn được truy cập qua **cùng các opcode**: `getfield`/`putfield` nếu instance, `getstatic`/`putstatic` nếu static. Không tồn tại bộ opcode “`getvolatile`” hoặc “`putvolatile`” riêng trong JVM chuẩn.

Ví dụ:

~~~java
class Flag {
    volatile boolean ready;
    static volatile int version;
}
~~~

Đây là class minh họa. Bytecode sử dụng `getfield`/`putfield` cho `ready` và `getstatic`/`putstatic` cho `version`; **field metadata** chứa flag `ACC_VOLATILE` để JVM thực thi theo quy tắc Java Memory Model (JMM).

- **Read volatile** có semantics về visibility và ordering; **write volatile** cũng có semantics tương ứng, bao gồm quan hệ *happens-before* của một write với những read sau đó của cùng field theo JMM.
- Các tác vụ đọc/ghi volatile đơn lẻ có bảo đảm atomic cho kiểu field theo JMM; ví dụ đọc/ghi `volatile long` và `volatile double` là atomic theo quy tắc Java.
- **`volatile int counter; counter++;` không atomic** như một phép read-modify-write. Nó gồm đọc, tính toán, ghi; hai thread vẫn có thể làm mất cập nhật.
- Nếu cần tăng atomic, dùng đồng bộ hóa, `AtomicInteger.incrementAndGet()`, hoặc công cụ concurrency phù hợp. Lời gọi API này không tạo opcode field access mới, dù JIT có thể sử dụng intrinsic/CPU instruction hiệu quả.

**Ngữ nghĩa JMM khác với lưu trữ vật lý.** Từ bytecode `getfield` một mình không thể kết luận field có volatile hay không; cần kiểm tra field declaration/flag trong class file. Với `javap -v -p`, phần khai báo field có thể hiển thị `ACC_VOLATILE`.

## 11. Compound assignment và expression result

Các phép gán như `obj.x += 3`, `obj.x++` hoặc `staticCount *= 2` **không có opcode “fieldAdd” riêng**. Compiler phối hợp:
1. Tính receiver/objectref nếu là instance field.
2. Đọc giá trị cũ bằng `getfield` hoặc `getstatic`.
3. Tính toán bằng opcode như `iadd`, `imul`; chuyển đổi nếu cần.
4. Ghi lại bằng `putfield` hoặc `putstatic`.
5. Nếu biểu thức gán có giá trị được sử dụng, có thể cần các opcode nhân bản stack như `dup`, `dup_x1`, `dup2_x1`, `dup2_x2` để giữ giá trị thích hợp.

Ví dụ:

~~~java
int result = (obj.count += 3);
~~~

Đây là snippet trong một phương thức với `obj` là instance phù hợp. `result` cần nhận **giá trị mới của biểu thức gán**; trong khi `putfield` tự nó không push gì. Compiler phải đảm bảo ngữ nghĩa biểu thức Java bằng cách tổ chức stack trước và sau lệnh ghi.

Tương tự, `obj.count++` (post-increment) biểu thức trả về **giá trị cũ**, còn `++obj.count` trả về **giá trị mới**. Chúng có thể dùng cùng `getfield`/`putfield` nhưng tổ chức stack khác nhau để giữ đúng kết quả.

## 12. Liên hệ class initialization với constructor

Hai loại initializer thường thấy khi đọc disassembly:

| Thành phần | Ký hiệu JVM | Chạy khi nào | Instruction field access thường gặp |
| --- | --- | --- | --- |
| Constructor | `<init>` | Khi tạo và khởi tạo một instance theo quy tắc Java | `putfield` cho instance field initializer/constructor assignment |
| Class/interface initializer | `<clinit>` | Khi class/interface cần initialization lần đầu theo quy tắc | `putstatic` cho static field initializer / static block |

Cần phân biệt:
- `new Counter()` đòi class được khởi tạo phù hợp, tạo object mới rồi chạy constructor; chính `<init>` là instance initializer trong thuật ngữ JVM.
- `<clinit>` là phương thức đặc biệt của class/interface được JVM gọi để initialization; người dùng không trực tiếp gọi nó bằng Java source như phương thức bình thường.
- `getstatic`/`putstatic` trên field đã resolve là một trigger initialization nếu class/interface khai báo field chưa được khởi tạo, nhưng `getfield`/`putfield` không có cơ chế trigger initialization tương tự **chỉ vì đọc/ghi instance field**. Thông thường object hợp lệ đã trải qua chuỗi khởi tạo thích hợp khi được tạo.

Đừng giải thích “`putfield` khởi tạo class khi ghi lần đầu”: đây là nhầm lẫn phổ biến giữa **constructor instance** và **class initialization**.

## 13. Các lỗi JVM liên quan đến bốn opcode

| Tình huống | Error/exception thường gặp | Cách hiểu đúng |
| --- | --- | --- |
| `getfield`/`putfield` với `objectref = null` | `NullPointerException` | Không có instance để đọc/ghi; thuộc bước chạy, sau kiểm tra liên kết liên quan. |
| Tham chiếu một field không còn tồn tại theo tên và descriptor | `NoSuchFieldError` | Class runtime không khớp binary mà caller kỳ vọng. |
| Cố dùng `getfield`/`putfield` cho field static hoặc `getstatic`/`putstatic` cho field instance | `IncompatibleClassChangeError` | Opcode không tương thích với loại field được resolve. |
| Bytecode truy cập field không còn được phép | `IllegalAccessError` | Quy tắc truy cập runtime hoặc quy định `final` bị vi phạm. |
| Owner class không thể được nạp | `NoClassDefFoundError` hoặc lỗi tải/liên kết thích hợp | Xem nguyên nhân và class loader, classpath/module path. |
| Static initialization ném exception | `ExceptionInInitializerError` hoặc lỗi liên quan | Xem stack trace và lỗi gốc của `<clinit>`. |
| Class file tự sinh có stack/type không hợp lệ | `VerifyError`/`ClassFormatError` | Bytecode không hợp lệ; cần xem verifier và class format. |

Khi sửa lỗi, hãy đọc **full exception chain**, class và descriptor mà runtime kỳ vọng. Ví dụ một bản thư viện đổi `static int total` thành `int total` trong khi caller không được biên dịch lại: bytecode cũ vẫn dùng `getstatic`, nên không thể “tự hiểu lại” thành `getfield` chỉ vì tên field trùng nhau.

### Không dùng reflection để đoán opcode trực tiếp

Khi viết `Field.get(target)` hoặc `Field.set(target, value)`, bytecode tại vị trí gọi là **lời gọi API reflection**. JVM/reflection implementation có thể dùng cơ chế nội bộ khác nhau, nhưng mã caller không tự nhiên xuất hiện một opcode `getfield` hướng trực tiếp vào field mục tiêu. Tương tự, các API như `VarHandle.get` và `VarHandle.set` diễn tả chế độ access linh hoạt; chúng không phải tên của instruction mới thay bốn opcode trên.

## 14. Checklist đọc `javap`

Khi tự disassemble một class bằng `javap -c -v -p`, kiểm tra theo thứ tự:

1. **Class và method hiện hành:** thuộc instance method, constructor `<init>`, static method hay `<clinit>`?
2. **Operand chỉ mục:** lệnh `getfield/putfield/getstatic/putstatic` chứa `#N` nào?
3. **Constant pool:** `#N` trỏ đến `CONSTANT_Fieldref` nào? Owner class, field name và descriptor là gì?
4. **Loại field:** field có flag `ACC_STATIC` hay không? Nếu `ACC_FINAL`, instruction ghi nằm ở vị trí hợp lệ?
5. **Stack:** instance read cần `objectref`; instance write cần `objectref` dưới `value`; static read/write không cần objectref.
6. **Instruction xung quanh:** load, constant, arithmetic, `dup`, `invoke*`, store cho thấy đây là một assignment, compound assignment hay access trong biểu thức.
7. **Class initialization:** với `getstatic/putstatic`, class/interface khai báo field có cần initialization vào thời điểm đó không?
8. **Đồng thời:** field có `ACC_VOLATILE`? Có read-modify-write cần atomicity lớn hơn từng access?
9. **Số index không ổn định:** không gán ý nghĩa cố định cho `#N` giữa các lần biên dịch; đọc chính file được kiểm tra.

### Một vài biểu thức nguồn thường gặp

| Biểu thức Java | Bytecode liên quan thường thấy | Ghi chú |
| --- | --- | --- |
| `obj.x` | `aload` + `getfield` | Receiver lấy từ biểu thức. |
| `obj.x = 42` | `aload` + nạp 42 + `putfield` | Thứ tự stack receiver, rồi value. |
| `Holder.x` nếu `x` static thường | `getstatic` | Có thể trigger class initialization. |
| `Holder.x = 42` nếu `x` static thường | nạp 42 + `putstatic` | Có thể trigger class initialization. |
| `obj.x++` | `getfield`, arithmetic, `putfield`, stack duplication nếu cần | Không bảo đảm atomic. |
| `staticCounter += 2` | `getstatic`, arithmetic, `putstatic` | Không bảo đảm atomic. |
| `Holder.CONSTANT` nếu compile-time constant | Lệnh nạp hằng (không nhất thiết `getstatic`) | Có thể inline. |
| `array.length` | `arraylength` | Không phải instance field opcode. |
| `obj.getX()` | `invokevirtual` / instruction invoke thích hợp | Method call, không phải trực tiếp getfield tại caller. |

## 15. Tự kiểm tra và đáp án

**Câu 1.** Điểm khác nhau cơ bản giữa `getfield` và `getstatic` là gì?

**Đáp án:** `getfield` đọc giá trị từ **một instance** và cần objectref trên stack; `getstatic` đọc field **của class/interface được resolve** và không cần objectref. `getstatic` có thể trigger initialization của class/interface khai báo field.

**Câu 2.** Trước `putfield`, operand stack cần chứa gì và theo thứ tự nào?

**Đáp án:** `..., objectref, value` từ đáy đến đỉnh. Giá trị mới ở trên, objectref ở dưới. Lệnh pop cả hai.

**Câu 3.** Có opcode `iincfield` để tăng `obj.count++` không?

**Đáp án:** Không. Compiler thường ghép `getfield`, instruction tính toán và `putfield`; `iinc` dành cho biến local int, không phải field.

**Câu 4.** `getstatic` có phải luôn xuất hiện khi đọc `static final int LIMIT = 7` không?

**Đáp án:** Không. Nếu là compile-time constant variable và caller được biên dịch với giá trị đó, compiler có thể inline 7 và không tạo `getstatic` cho field tại call site.

**Câu 5.** Instance field `int number` của Parent và Child cùng tên là override hay hiding?

**Đáp án:** **Hiding**. Field được chọn theo quy tắc liên kết tại compile time và symbolic reference, không virtual dispatch như instance method.

**Câu 6.** `getfield` với objectref null gây gì? `getstatic` có cần objectref không?

**Đáp án:** Sau khi kiểm tra liên kết phù hợp, `getfield` trên null ném `NullPointerException`. `getstatic` không có objectref operand.

**Câu 7.** Nếu caller đã biên dịch truy cập `static int total` nhưng runtime đổi thành `int total`, sẽ thế nào?

**Đáp án:** Bytecode cũ dùng `getstatic/putstatic` trên field nay là instance; khi resolution và thực thi theo kiểm tra JVM, có thể gặp `IncompatibleClassChangeError`. Cần giải quyết incompatibility nhị phân và biên dịch/triển khai nhất quán.

**Câu 8.** Một field `volatile int counter` được tăng bằng `counter++` có atomic không?

**Đáp án:** Không. Read volatile và write volatile có quy tắc visibility/ordering, nhưng chuỗi read-modify-write không tự thành atomic. Có thể dùng `AtomicInteger` hoặc synchronization.

**Câu 9.** `putstatic` ghi một `static final` vào lúc tùy ý sau initialization có hợp lệ không?

**Đáp án:** Không. Bytecode hợp lệ phải tuân thủ quy tắc của JVMS: field `final` thuộc class/interface hiện tại và instruction `putstatic` phải nằm trong initializer `<clinit>` của class/interface ấy.

**Câu 10.** Vì sao chỉ mục `#18` sau `getfield` không phải “offset 18 byte” trong object?

**Đáp án:** Vì operand đó là chỉ mục của `CONSTANT_Fieldref` trong run-time constant pool. JVM resolve symbol sang field phù hợp; layout vật lý là chi tiết runtime implementation.

## 16. Bản đồ ghi nhớ

~~~text
                         TRUY CẬP FIELD
                              |
                   +----------+----------+
                   |                     |
                INSTANCE                STATIC
             cần objectref          không cần objectref
                   |                     |
           +-------+-------+     +-------+-------+
           |               |     |               |
         READ            WRITE  READ            WRITE
       getfield         putfield getstatic       putstatic

Stack:
  getfield : ..., ref        -> ..., value
  putfield : ..., ref, value -> ...
  getstatic: ...             -> ..., value
  putstatic: ..., value      -> ...

Mỗi lệnh -> #N CONSTANT_Fieldref -> Class + NameAndType
            -> owner class, field name, field descriptor
            -> resolution + access/field-kind checks

Static access (getstatic/putstatic)
            -> có thể kích hoạt class/interface initialization
Instance access (getfield/putfield)
            -> cần objectref không null

final       -> hạn chế ghi ở <init>/<clinit> theo loại field
volatile    -> thêm memory-order/visibility; không biến ++ thành atomic
~~~

**Điểm cốt lõi:** Bốn opcode đều truy cập field qua **symbolic reference** từ constant pool, nhưng khác nhau ở **instance/static**, **đọc/ghi**, **hình dạng operand stack** và **điều kiện runtime**. Đọc disassembly đúng cần ghép opcode với descriptor, flags, class initialization và quy tắc Java Memory Model.

## Tài liệu tham khảo

- [JVMS Java SE 21 — 6.5 Instructions: getfield, putfield, getstatic, putstatic](https://docs.oracle.com/javase/specs/jvms/se21/html/jvms-6.html#jvms-6.5).
- [JVMS Java SE 21 — 4.4.2 Fieldref, Methodref và InterfaceMethodref](https://docs.oracle.com/javase/specs/jvms/se21/html/jvms-4.html#jvms-4.4.2).
- [JVMS Java SE 21 — 5.4.3.2 Field Resolution](https://docs.oracle.com/javase/specs/jvms/se21/html/jvms-5.html#jvms-5.4.3.2).
- [JVMS Java SE 21 — 5.5 Initialization](https://docs.oracle.com/javase/specs/jvms/se21/html/jvms-5.html#jvms-5.5).
- [JLS Java SE 21 — 12.4 Initialization of Classes and Interfaces](https://docs.oracle.com/javase/specs/jls/se21/html/jls-12.html#jls-12.4).
- [JLS Java SE 21 — 17.4 Memory Model](https://docs.oracle.com/javase/specs/jls/se21/html/jls-17.html#jls-17.4).
- [JDK 21 — javap tool](https://docs.oracle.com/en/java/javase/21/docs/specs/man/javap.html).


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="bytecode-instructions-009">checkcast, instanceof, arraylength và kiểm tra khi truy cập mảng</a>

<details>
<summary>Xem chi tiết</summary>

# `checkcast`, `instanceof`, `arraylength` và kiểm tra khi truy cập mảng

Trong mô hình thực thi JVM, **`checkcast`**, **`instanceof`** và **`arraylength`** là ba bytecode instruction có mục đích khác nhau. Hai lệnh đầu làm việc với **giá trị tham chiếu và kiểu đối tượng tại runtime**; lệnh thứ ba lấy **độ dài của đối tượng mảng**. Khi chương trình truy cập phần tử mảng, các lệnh như `iaload`, `aastore` còn thực hiện những kiểm tra riêng về **null**, **chỉ số hợp lệ** và **kiểu phần tử được lưu**.

Hiểu các lệnh này giúp giải thích vì sao Java có thể biên dịch thành công nhưng khi chạy lại gặp `ClassCastException`, `NullPointerException`, `ArrayIndexOutOfBoundsException` hoặc `ArrayStoreException`. Đồng thời, nó cho thấy JVM bytecode có những kiểm tra động **khác** với bytecode verifier: verifier kiểm tra tính hợp lệ về cấu trúc/kiểu của mã trước khi thực thi, còn các instruction thực hiện kiểm tra trên **giá trị thực tế của chương trình**.

## 1. Bức tranh tổng quan

| Instruction/nhóm | Đối tượng thao tác | Đầu vào trên operand stack | Kết quả chính | Lỗi runtime điển hình |
| --- | --- | --- | --- | --- |
| `checkcast` | Kiểu tham chiếu đích | Một reference | Giữ lại chính reference nếu cast hợp lệ | `ClassCastException` |
| `instanceof` | Kiểu tham chiếu đích | Một reference | `int` 1 hoặc 0 | Không ném `ClassCastException` khi không khớp kiểu |
| `arraylength` | Mảng | Một array reference | `int` là độ dài | `NullPointerException` nếu reference là `null` |
| `*aload` | Phần tử mảng | Array reference và chỉ số `int` | Giá trị phần tử | `NullPointerException`, `ArrayIndexOutOfBoundsException` |
| `*astore` | Phần tử mảng | Array reference, chỉ số, giá trị ghi | Không đẩy kết quả | `NullPointerException`, `ArrayIndexOutOfBoundsException`; với `aastore` còn có thể là `ArrayStoreException` |

Trong bảng, `*aload` và `*astore` là **tên nhóm**, không phải tên opcode hợp lệ để ghi nguyên văn vào class file. JVM có những opcode riêng như `iaload`, `aaload`, `iastore`, `aastore`.

Ba nguyên tắc cần nhớ:

1. **`checkcast` kiểm tra khả năng chuyển reference sang kiểu đích**, không tạo object mới, không sửa runtime class của object.
2. **`instanceof` hỏi một reference có tương thích với kiểu đích hay không**; nó trả `0` hoặc `1` trên operand stack, sau đó bytecode rẽ nhánh có thể sử dụng kết quả.
3. **Mảng Java là object có độ dài cố định**, và các instruction truy cập phần tử phải xử lý null, bounds và (với reference array) khả năng gán kiểu tại runtime.

## 2. Nhắc lại: reference, operand stack và kiểu runtime

Trong Java:

```java
Object value = "JVM";
String text = (String) value;
```

`value` được khai báo với kiểu tĩnh `Object`, nhưng ở runtime nó giữ reference tới một `String`. Biến `text` nhận cùng reference sau phép cast hợp lệ. Trình biên dịch Java xác minh cách viết có thể cast theo quy tắc ngôn ngữ; JVM **kiểm tra object thực tế** khi chạy bytecode `checkcast`.

Với operand stack, các reference đều là **category 1**. Ký hiệu dưới đây viết theo thứ tự **dưới → trên**; `value` nằm ở đỉnh:

```text
... , value
            ↑
           TOP
```

Một giá trị reference trên stack không phải toàn bộ nội dung object được sao chép vào stack. JVM sử dụng nó để truy cập object hay array. Khi `checkcast` thành công, **reference vẫn như cũ**. `instanceof` thì tiêu thụ reference và **đẩy kết quả `int`**. Những chi tiết này quyết định instruction kế tiếp có thể làm gì.

## 3. `checkcast`: kiểm tra kiểu reference khi chạy

### 3.1. Cú pháp bytecode và hiệu ứng stack

Opcode `checkcast` là **`0xC0`**. Instruction mang một chỉ số hai byte tham chiếu tới thông tin kiểu đích trong constant pool (một `CONSTANT_Class` phù hợp).

```text
Trước: ..., objectref
checkcast <target-type>
Sau nếu thành công: ..., objectref
```

- Nếu `objectref` là **`null`**, `checkcast` **thành công**, giữ `null` trên stack.
- Nếu reference khác `null` và object runtime tương thích với kiểu đích, instruction **thành công**, giữ **chính reference đó**.
- Nếu object không thể chuyển sang kiểu đích, instruction ném **`ClassCastException`**.
- Việc phân giải kiểu đích từ constant pool cũng có thể gặp vấn đề liên kết/lớp trong những tình huống riêng; đừng mặc định mọi lỗi nạp class đều là `ClassCastException`.

Ví dụ hợp lệ:

```java
Object value = "hello";
String text = (String) value;
System.out.println(text.length());
```

Đây là **đoạn mã đặt trong một method**, không phải chương trình hoàn chỉnh. `value` giữ một `String`; compiler có thể dùng `checkcast java/lang/String` sau khi nạp reference để bảo đảm loại object trước khi gọi `String.length()`.

Ví dụ không hợp lệ tại runtime:

```java
Object value = Integer.valueOf(42);
String text = (String) value; // ClassCastException tại runtime
```

Đoạn này vẫn có thể **biên dịch**, vì kiểu tĩnh `Object` cho phép cast tường minh sang `String`. Nhưng object thực tế là `Integer`. Không có thao tác chuyển nội dung số `42` thành chuỗi `"42"` ở instruction `checkcast`.

### 3.2. Cast không tương đương chuyển đổi giá trị

```java
Object number = Integer.valueOf(42);
String converted = String.valueOf(number); // "42"
```

`String.valueOf` thực hiện thao tác tạo/nhận chuỗi theo API, còn `(String) number` kiểm tra **khả năng dùng reference như String**. Chúng có ngữ nghĩa hoàn toàn khác.

Phép cast nguyên thủy cũng khác: `(int) 3.7` dùng cơ chế chuyển đổi số tương ứng (chẳng hạn `d2i`), **không** dùng `checkcast` vì toán hạng không phải reference.

### 3.3. Kiểm tra kiểu được làm ở đâu?

`javac` kiểm tra tính hợp lệ của biểu thức cast theo **hệ thống kiểu nguồn**. Khi cần một phép cast reference còn phải kiểm tra động, compiler sinh `checkcast`. Những cast chắc chắn thừa có thể không sinh instruction kiểm tra riêng; do đó **không phải mọi dấu ngoặc cast trong source đều ứng với một opcode `checkcast`**.

Trong Java generic sau type erasure, compiler cũng có thể thêm `checkcast` sau khi lấy giá trị từ API generic đã xóa kiểu. Ví dụ gán một kết quả `List<String>.get(...)` vào `String` có thể cần `checkcast java/lang/String` tại bytecode, dù source không viết cast tường minh.

**Lưu ý generic:** JVM có thể kiểm tra runtime object có là `List` hay không nhưng thường không có cơ chế từ một reference đơn lẻ để xác minh mọi phần tử của nó đều là `String`. Tham số generic đã được erase ở descriptor; đây là lý do unchecked cast và heap pollution cần được hiểu riêng.

## 4. `instanceof`: kiểm tra kiểu và cho ra `int` 0/1

Opcode `instanceof` là **`0xC1`**; nó cũng tham chiếu kiểu đích qua chỉ số constant pool.

```text
Trước: ..., objectref
instanceof <target-type>
Sau:   ..., result (int)
```

- Reference là `null` → kết quả **`0`**.
- Reference không `null`, object tương thích với target type → **`1`**.
- Reference không `null`, object không tương thích → **`0`**.
- `instanceof` không ném `ClassCastException` chỉ vì kiểu không khớp. Tuy nhiên những lỗi về phân giải kiểu được chỉ định có thể tồn tại độc lập.

Ở Java source, `instanceof` có kết quả **`boolean`**. Ở mức operand stack của JVM, giá trị điều kiện được biểu diễn bằng `int` 0/1 và có thể dùng với các lệnh như `ifeq` hoặc `ifne`.

Ví dụ:

```java
Object value = "abc";
if (value instanceof String) {
    System.out.println("Đây là String");
}
```

Đây là đoạn mã đặt trong method; compiler có thể sinh `instanceof java/lang/String`, rồi `ifeq` để bỏ qua nhánh nếu kết quả bằng 0.

### 4.1. `instanceof` và `checkcast` thường đi cùng nhau

Đoạn source:

```java
static int textLength(Object value) {
    if (value instanceof String) {
        return ((String) value).length();
    }
    return -1;
}
```

là **method phụ trợ**, cần đặt trong một class Java để biên dịch. Ở các compiler điển hình, nhánh điều kiện dùng `instanceof`, và khi truy cập `String.length()`, một `checkcast` giúp đưa reference về dạng mà bytecode dùng cho thao tác đó.

Tuy hai instruction thường đứng gần nhau, **chúng không thay thế nhau**:

| Trường hợp | `instanceof String` | `(String) value` / `checkcast String` |
| --- | --- | --- |
| `value` là reference tới `String` | `true` (bytecode trả 1) | Thành công, giữ reference |
| `value` là reference tới `Integer` | `false` (bytecode trả 0) | Ném `ClassCastException` |
| `value` là `null` | `false` (bytecode trả 0) | Thành công, kết quả vẫn `null` |

**Đặc biệt:** `(String) null` **không tự gây `NullPointerException`**. Nhưng nếu dùng kết quả `null` để gọi `length()`, câu lệnh gọi instance method có thể gây `NullPointerException`.

### 4.2. Pattern matching cho `instanceof` (Java 17)

Java 17 hỗ trợ pattern matching kiểu:

```java
static String describe(Object value) {
    if (value instanceof String text) {
        return "String length=" + text.length();
    }
    return "other";
}
```

`text` là **pattern variable** được compiler giới hạn phạm vi trong nhánh biết chắc kiểu khớp. Điều này làm source ngắn hơn và tránh việc lập trình viên viết lại cast thủ công; **không có nghĩa JVM có opcode riêng tên `instanceof_pattern`**. Bytecode có thể vẫn gồm `instanceof`, rẽ nhánh, cast khi cần và store vào local variable. Xem `javap -c` để biết compiler cụ thể tạo gì.

## 5. Kiểu array cũng tham gia `checkcast` và `instanceof`

Mảng Java là object; mỗi mảng có **runtime array class** với element type và số chiều. Vì vậy các phép kiểm tra reference cũng áp dụng cho mảng:

```java
Object values = new String[2];
boolean b1 = values instanceof String[]; // true
boolean b2 = values instanceof Object[]; // true
boolean b3 = values instanceof int[];    // false
String[] names = (String[]) values;       // thành công
```

Đây là đoạn source đặt trong method. Nó minh họa `String[]` là một reference type, không cần phần tử mảng đang chứa một chuỗi cụ thể nào để `instanceof String[]` trả `true`.

Các kiểu mảng reference có quy tắc **covariance**: ví dụ `String[]` có thể được tham chiếu qua `Object[]`. Nhưng runtime component type của object vẫn là `String`; khi ghi phần tử, `aastore` phải kiểm tra khả năng gán.

```java
Object[] base = new String[1];
base[0] = "ok";
base[0] = new Object(); // ArrayStoreException tại runtime
```

Đoạn này **không phải** lỗi `ClassCastException`. Phép gán biến `Object[] base = new String[1]` được phép theo covariance. Lỗi chỉ phát sinh khi thực sự cố **lưu một đối tượng không tương thích** vào mảng có runtime component type là `String`.

Mảng primitive như `int[]` cũng là object, nhưng **không phải `Object[]`**, vì phần tử của nó không phải reference. Một `int[]` có thể gán vào biến kiểu `Object`, nhưng không thể gán trực tiếp vào biến kiểu `Object[]`.

## 6. `arraylength`: lấy số phần tử của mảng

Opcode `arraylength` là **`0xBE`**, không có toán hạng constant-pool index.

```text
Trước: ..., arrayref
arraylength
Sau:   ..., length (int)
```

- Nếu `arrayref` là reference tới mảng hợp lệ, JVM đọc **độ dài của mảng** và đẩy kết quả kiểu `int`.
- Nếu `arrayref == null`, instruction ném **`NullPointerException`**.
- Độ dài mảng là **số phần tử**, không phải dung lượng byte. Ví dụ `new long[3]` có `length == 3`, không phải 24.
- Độ dài mảng không thay đổi sau khi object mảng được tạo. Muốn số phần tử khác, tạo một đối tượng mảng khác hoặc dùng cấu trúc động như `ArrayList`.

Ví dụ:

```java
int[] values = {10, 20, 30};
System.out.println(values.length); // 3
```

Điểm khác biệt quan trọng: cú pháp Java `values.length` là **thuộc tính đặc biệt của array type**, và compiler thường sinh opcode **`arraylength`**, không phải `getfield length` của một class Java thông thường. Đối với `String`, `text.length()` là **lời gọi method**, hoàn toàn khác.

### `arraylength` không kiểm tra bounds

`arraylength` chỉ cần array reference; nó **không nhận chỉ số phần tử**. Kiểm tra chỉ số diễn ra ở `*aload`/`*astore`. Ví dụ:

```java
int[] values = new int[2];
int n = values.length; // n = 2, không truy cập phần tử
int x = values[2];     // ArrayIndexOutOfBoundsException
```

Đây là đoạn minh họa đặt trong method, không phải một chương trình chạy độc lập. Với một mảng có độ dài `n`, chỉ số hợp lệ là **`0 <= index < n`**; chỉ số âm cũng không hợp lệ.

### Mảng nhiều chiều

Java biểu diễn mảng nhiều chiều dưới dạng **mảng chứa các mảng con**:

```java
int[][] grid = { {1, 2, 3}, {4} };
System.out.println(grid.length);    // 2: số hàng
System.out.println(grid[0].length); // 3: số phần tử hàng đầu
System.out.println(grid[1].length); // 1: độ dài hàng thứ hai
```

`grid.length` và `grid[0].length` là hai lần `arraylength` áp dụng lên **hai array reference khác nhau**. Java không buộc mọi mảng con phải có cùng độ dài. Nếu một hàng bằng `null`, dùng `grid[i].length` có thể gặp `NullPointerException`.

## 7. Các lệnh `*aload`: đọc phần tử mảng

Mỗi loại phần tử có instruction thích hợp:

| Opcode | Mảng tương ứng | Loại giá trị được đẩy |
| --- | --- | --- |
| `iaload` | `int[]` | `int` |
| `laload` | `long[]` | `long` (category 2) |
| `faload` | `float[]` | `float` |
| `daload` | `double[]` | `double` (category 2) |
| `aaload` | Mảng kiểu tham chiếu, ví dụ `String[]` | Reference |
| `baload` | `byte[]` hoặc `boolean[]` | Giá trị được đưa lên operand stack theo dạng `int` |
| `caload` | `char[]` | `int` (zero-extended từ `char`) |
| `saload` | `short[]` | `int` (sign-extended từ `short`) |

`baload` khi đọc `byte[]` sign-extend byte có dấu thành `int`; với `boolean[]`, biểu diễn true/false tuân thủ quy tắc tương ứng của JVM. `caload` giữ giá trị không dấu của `char` khi đưa lên stack.

Hiệu ứng stack tổng quát:

```text
Trước: ..., arrayref, index(int)
*aload
Sau:   ..., value
```

Các kiểm tra quan trọng:

1. Array reference phải khác `null`, nếu không → **`NullPointerException`**.
2. Chỉ số phải trong phạm vi `0..length-1`, nếu không → **`ArrayIndexOutOfBoundsException`**.
3. Operand và opcode phải phù hợp với kiểu mảng theo các ràng buộc về kiểu của bytecode; JVM verifier bảo vệ khỏi việc tùy ý dùng `iaload` như thể tham chiếu tới `String[]` là mảng `int[]`.

**Khi cả array reference là `null` và index là giá trị ngoài phạm vi**, không có mảng hợp lệ để xác định bounds: lỗi null sẽ xảy ra thay vì kết luận về bounds của một đối tượng không tồn tại. Trên một mảng hợp lệ, chỉ số âm hay `>= length` đều sai.

### Ví dụ đọc kiểu hẹp

```java
byte[] bytes = {(byte) 0xFF};
char[] chars = {(char) 255};
short[] shorts = {(short) -2};

System.out.println(bytes[0]);       // -1
System.out.println((int) chars[0]); // 255
System.out.println(shorts[0]);      // -2
```

Đoạn ví dụ này đặt trong `main` hoặc method tương đương. Sự khác biệt `-1` và `255` giúp thấy `byte` là kiểu có dấu, còn `char` là đơn vị mã UTF-16 không dấu; compiler/JVM thực hiện chuyển đổi tương ứng khi đẩy giá trị lên operand stack.

## 8. Các lệnh `*astore`: ghi phần tử mảng

| Opcode | Mảng được ghi | Giá trị ghi |
| --- | --- | --- |
| `iastore` | `int[]` | `int` |
| `lastore` | `long[]` | `long` |
| `fastore` | `float[]` | `float` |
| `dastore` | `double[]` | `double` |
| `aastore` | Mảng reference như `Object[]`, `String[]` | Reference, có kiểm tra runtime component type |
| `bastore` | `byte[]`, `boolean[]` | Giá trị phù hợp được xử lý từ dạng `int` |
| `castore` | `char[]` | Giá trị phù hợp được chuyển về `char` |
| `sastore` | `short[]` | Giá trị phù hợp được chuyển về `short` |

Hiệu ứng stack tổng quát:

```text
Trước: ..., arrayref, index(int), value
*astore
Sau:   ...
```

`lastore` và `dastore` tiêu thụ một giá trị **category 2**; các opcode còn lại nhận loại toán hạng theo quy tắc tương ứng. Sau khi store thành công, instruction **không để lại chính giá trị đã ghi** trên stack. Nếu biểu thức gán phải có kết quả, compiler có thể dùng `dup_x2` hoặc `dup2_x2` thích hợp để giữ bản sao giá trị trước khi store.

### 8.1. Ba loại kiểm tra khi ghi

Với tham chiếu mảng:

- **Null check**: array reference là `null` → `NullPointerException`.
- **Bounds check**: index nhỏ hơn 0 hoặc lớn hơn/equal length → `ArrayIndexOutOfBoundsException`.
- **Store type check cho `aastore`**: với phần tử ghi khác `null`, runtime object của phần tử phải phù hợp với **runtime component type của chính object mảng**; nếu không → `ArrayStoreException`.

Cần tách **static type của biến mảng** khỏi **runtime type của object mảng**:

```java
Object[] slots = new String[2];
slots[0] = "ok";       // hợp lệ
slots[1] = new Object(); // ArrayStoreException tại runtime
```

Compiler chấp nhận gán `new Object()` vào phần tử biến có kiểu tĩnh `Object[]`. Nhưng object mảng thực tế là `String[]`, nên `aastore` phải từ chối object không phải `String`. Kiểm tra kiểu này **không phải `checkcast`** và lỗi là **`ArrayStoreException`**, không phải `ClassCastException`.

### 8.2. Giá trị `null` và mảng reference

`null` có thể ghi vào bất kỳ mảng reference phù hợp nào:

```java
String[] words = new String[1];
words[0] = null; // hợp lệ
```

Dù giá trị ghi là `null` và không có object để kiểm tra runtime class, **null/bounds check đối với chính mảng vẫn cần được đáp ứng**. Chẳng hạn `words[9] = null` vẫn vượt bounds.

Mảng primitive không có `null` cho từng phần tử: `int[]` chứa các giá trị `int`, không phải `Integer` reference. Nếu cần các phần tử nullable, có thể dùng `Integer[]`, nhưng nó là loại mảng khác và việc boxing/unboxing có thêm quy tắc.

## 9. Khác biệt giữa bytecode verifier và kiểm tra động

Một lỗi phổ biến là nói “JVM đã verify bytecode rồi nên truy cập mảng không thể sai”. Thực tế có hai tầng kiểm tra khác nhau:

| Tầng | Kiểm tra ví dụ | Thời điểm/mục đích |
| --- | --- | --- |
| Trình biên dịch `javac` | Kiểu biểu thức `a[i]`, tính khả thi của cast, loại biến, cú pháp | Trước khi tạo class file |
| Bytecode verification / linking | Kiểu toán hạng trên operand stack, stack frame, control flow, tính phù hợp của `iaload`/`arraylength` | Khi JVM xác minh/chuẩn bị dùng class file theo quy tắc JVM |
| Kiểm tra động tại instruction | Object cast được không, reference có null không, index có trong bounds không, phần tử có đúng runtime array component type không | **Trong lúc thực thi**, phụ thuộc dữ liệu runtime |

Bytecode verifier không thể biết trước giá trị thực của một index nhận từ người dùng hoặc một object được trả về từ thư viện. Bởi vậy một class file hoàn toàn hợp lệ vẫn có thể ném exception tại runtime.

JIT có thể tối ưu để bỏ **những lần kiểm tra không cần thiết về mặt quan sát được** khi chứng minh chúng dư thừa, chẳng hạn hoisting/eliminating một số bounds check. Điều này không thay đổi ngữ nghĩa phải được bảo toàn: trường hợp đáng ra ném exception vẫn phải có hành vi phù hợp với mô hình Java. Không dùng sự tồn tại của JIT để kết luận các instruction không có kiểm tra runtime.

## 10. Ví dụ Java 17+ chạy được: cast, `instanceof`, độ dài và truy cập mảng

Tạo một tệp **`CastArrayDemo.java`** tại thư mục làm việc:

```java
public class CastArrayDemo {
    static String castText(Object value) {
        return (String) value;
    }

    static String describe(Object value) {
        if (value instanceof String text) {
            return "String(" + text.length() + ")";
        }
        return "other";
    }

    static int arraySize(int[] data) {
        return data.length;
    }

    static int read(int[] data, int index) {
        return data[index];
    }

    static void write(int[] data, int index, int value) {
        data[index] = value;
    }

    public static void main(String[] args) {
        Object text = "JVM";
        Object number = Integer.valueOf(42);
        Object absent = null;

        System.out.println("instanceof text: " + (text instanceof String));
        System.out.println("instanceof number: " + (number instanceof String));
        System.out.println("instanceof null: " + (absent instanceof String));
        System.out.println("pattern: " + describe(text));
        System.out.println("cast: " + castText(text));
        System.out.println("cast null: " + (castText(absent) == null));

        try {
            castText(number);
        } catch (ClassCastException e) {
            System.out.println("bad cast: ClassCastException");
        }

        int[] scores = {5, 8};
        System.out.println("length: " + arraySize(scores));
        System.out.println("read: " + read(scores, 1));
        write(scores, 0, 11);
        System.out.println("after write: " + scores[0]);

        byte[] bytes = {(byte) 0xFF};
        char[] chars = {(char) 255};
        System.out.println("byte load: " + bytes[0]);
        System.out.println("char load: " + (int) chars[0]);

        try {
            read(scores, 2);
        } catch (ArrayIndexOutOfBoundsException e) {
            System.out.println("bad index: ArrayIndexOutOfBoundsException");
        }

        try {
            arraySize(null);
        } catch (NullPointerException e) {
            System.out.println("null array: NullPointerException");
        }

        Object[] slots = new String[1];
        slots[0] = "ok";
        System.out.println("stored: " + slots[0]);
        try {
            slots[0] = new Object();
        } catch (ArrayStoreException e) {
            System.out.println("bad store: ArrayStoreException");
        }

        Object rawArray = new String[2];
        System.out.println("instanceof String[]: "
                + (rawArray instanceof String[]));
        System.out.println("cast array length: "
                + ((String[]) rawArray).length);
    }
}
```

Với JDK 17 trở lên:

```bash
javac -encoding UTF-8 CastArrayDemo.java
java CastArrayDemo
```

**Standard output chính xác:**

```text
instanceof text: true
instanceof number: false
instanceof null: false
pattern: String(3)
cast: JVM
cast null: true
bad cast: ClassCastException
length: 2
read: 8
after write: 11
byte load: -1
char load: 255
bad index: ArrayIndexOutOfBoundsException
null array: NullPointerException
stored: ok
bad store: ArrayStoreException
instanceof String[]: true
cast array length: 2
```

Giải thích:

1. `text` trỏ tới `String` nên `instanceof` là `true`; `number` trỏ tới `Integer` nên không khớp; `absent == null` làm `instanceof` trả `false`.
2. `describe(text)` dùng **pattern matching cho `instanceof`** và trả độ dài chuỗi 3.
3. `castText(text)` thành công; `castText(absent)` vẫn trả `null`; chỉ `castText(number)` ném `ClassCastException`.
4. `scores.length` là 2; chỉ số 1 hợp lệ và đọc ra 8. Sau khi ghi `scores[0] = 11`, mảng giữ giá trị 11.
5. Giá trị byte `0xFF` tương ứng `-1` khi diễn giải `byte` có dấu; `char` 255 là không dấu.
6. Đọc `scores[2]` vượt phạm vi `0..1` nên bị bắt dưới dạng `ArrayIndexOutOfBoundsException`.
7. `arraySize(null)` thực hiện `arraylength` trên reference `null`, gây `NullPointerException`.
8. `slots` có kiểu biến `Object[]` nhưng object là `String[]`. Ghi `"ok"` hợp lệ, ghi `new Object()` bị từ chối bằng `ArrayStoreException`.
9. Reference `rawArray` trỏ tới `String[]`; cả kiểm tra `instanceof String[]` và `checkcast` sang `String[]` đều hợp lệ. Cast không tạo mảng thứ hai.

Ví dụ cố ý **bắt exception theo loại** và in chuỗi cố định, vì thông báo chi tiết trong `e.getMessage()` có thể thay đổi theo JVM và phiên bản. Nhờ vậy stdout trên là kết quả ổn định của chương trình.

## 11. Dùng `javap` để đọc bytecode thật

Từ thư mục có `CastArrayDemo.class`:

```bash
javap -c -p CastArrayDemo
javap -v -p CastArrayDemo
```

`-c` in bytecode, `-p` cho phép xem cả những method không public; `-v` hiển thị thêm constant pool, descriptor, exception table và thuộc tính ClassFile.

Tìm các method/đoạn như sau:

| Source/method | Bytecode điển hình | Ý nghĩa |
| --- | --- | --- |
| `castText(Object)` | `aload_0`, `checkcast java/lang/String`, `areturn` | Kiểm tra reference rồi trả reference |
| `describe(Object)` | `instanceof java/lang/String`, rẽ nhánh, có thể `checkcast` | Quyết định nhánh và gán pattern variable |
| `arraySize(int[])` | `aload_0`, `arraylength`, `ireturn` | Lấy độ dài mảng thành `int` |
| `read(int[], int)` | `aload_0`, `iload_1`, `iaload`, `ireturn` | Lấy phần tử `int` có kiểm tra null/bounds |
| `write(int[], int, int)` | `aload_0`, `iload_1`, `iload_2`, `iastore`, `return` | Ghi phần tử `int` |
| `slots[0] = new Object()` | `aastore` | Kiểm tra runtime component type khi lưu reference |

Các dòng trên mô tả **instruction quan trọng**; offset, constant pool index và cách compiler tạo pattern matching/string concatenation có thể khác giữa JDK. Không thay thế việc xem `javap` thực tế bằng một bản listing giả định.

### 11.1. Operand stack qua `checkcast`

```text
aload_0
  ..., objectref
checkcast java/lang/String
  ..., objectref         // giữ cùng giá trị nếu thành công
areturn
  ...                    // reference được trả về caller
```

### 11.2. Operand stack qua `instanceof`

```text
aload_0
  ..., objectref
instanceof java/lang/String
  ..., result(int: 0 hoặc 1)
ifeq target
  ...                    // điều kiện được lấy khỏi stack để rẽ nhánh
```

### 11.3. Operand stack qua `arraylength` và `iaload`

```text
aload_0                    // int[] reference
arraylength                // int length
ireturn

aload_0                    // int[] reference
iload_1                    // int index
iaload                     // int element
ireturn
```

Các mảnh này là **sơ đồ minh họa**, không phải một file `.jasm` cần chạy độc lập. Nếu mảng hoặc index không hợp lệ, instruction ném exception thay vì đặt kết quả bình thường lên stack.

### 11.4. `aastore` có thể lỗi mà không cần `checkcast`

Khi biên dịch `Object[] slots = new String[1]`, biến `slots` có kiểu tĩnh `Object[]`. Lệnh ghi một object qua biến đó có thể trực tiếp dùng **`aastore`**, không cần thêm `checkcast String` vào source/bytecode. **Bản thân `aastore`** chịu trách nhiệm đảm bảo đối tượng đang lưu tương thích với kiểu phần tử của mảng thực tế. Đây là điểm giúp phân biệt `ArrayStoreException` và `ClassCastException`.

## 12. Ma trận lỗi và cách suy luận đúng thứ tự

| Tình huống | Lệnh/hoạt động | Kết quả |
| --- | --- | --- |
| `(String) Integer.valueOf(42)` sau khi kiểu tĩnh cho phép cast | `checkcast` | `ClassCastException` |
| `null instanceof String` | `instanceof` | `false` / bytecode `0` |
| `(String) null` | `checkcast` | Thành công, giữ `null` |
| `((String) null).length()` | Cast rồi gọi method | Cast hợp lệ, lần dereference gây `NullPointerException` |
| `((int[]) null).length` | `arraylength` | `NullPointerException` |
| `new int[2]` rồi đọc index `-1` hoặc `2` | `iaload` | `ArrayIndexOutOfBoundsException` |
| `new int[2]` rồi ghi index `2` | `iastore` | `ArrayIndexOutOfBoundsException` |
| `Object[] a = new String[1]; a[0] = new Object();` | `aastore` | `ArrayStoreException` |
| `Object[] a = new String[1]; a[0] = null;` | `aastore` | Hợp lệ |
| `new long[3]` và `.length` | `arraylength` | `3` dưới dạng `int` |

**Không đánh đồng thứ tự đánh giá của Java source với thứ tự kiểm tra trong một instruction.** Trong một biểu thức phức tạp, Java có quy tắc riêng về việc đánh giá toán hạng và tác dụng phụ. Khi đã tới `*aload`/`*astore`, instruction làm việc với các giá trị chuẩn bị sẵn trên operand stack và kiểm tra null/bounds/type theo đặc tả. Nếu cần xác định exception nào xuất hiện trong một biểu thức có nhiều subexpression cùng lỗi, hãy xem cả thứ tự đánh giá nguồn và bytecode của `javap`.

### Phân biệt lỗi với lỗi tạo mảng

`NegativeArraySizeException` phát sinh khi tạo mảng với kích thước âm trong các instruction như `newarray` hoặc `anewarray`, **không phải** do `arraylength`. `OutOfMemoryError` cũng có thể phát sinh khi cấp phát mảng nếu runtime không đủ tài nguyên. Chúng liên quan vòng đời object mảng nhưng không thay thế các kiểm tra truy cập mảng trong bài này.

## 13. Những nhầm lẫn phổ biến

| Nhầm lẫn | Cách hiểu đúng |
| --- | --- |
| “`checkcast` chuyển `Integer` thành `String`.” | Nó kiểm tra khả năng dùng **cùng reference** dưới kiểu đích; không chuyển nội dung object |
| “Cast `null` sang `String` ném NPE.” | Cast `null` thành công; NPE chỉ xuất hiện khi thực hiện thao tác dereference không hợp lệ |
| “`instanceof` trả reference mới nếu đúng kiểu.” | Nó lấy reference khỏi stack và đẩy `int` 1/0 |
| “Source có `instanceof` thì bytecode luôn chỉ có đúng một opcode.” | Compiler có thể thêm rẽ nhánh, `checkcast` và lưu pattern variable tùy logic |
| “`array.length` là `getfield` như field thông thường.” | Mảng dùng instruction riêng `arraylength` |
| “`arraylength` kiểm tra index của mảng.” | Nó không có toán hạng index; `*aload`/`*astore` kiểm tra bounds |
| “Một mảng `Object[]` có thể chứa bất kỳ object nào.” | Nếu object mảng thực tế là `String[]`, `aastore` chỉ cho giá trị tương thích với `String` hoặc `null` |
| “`ArrayStoreException` là `ClassCastException`.” | Đây là hai exception khác nhau, do các phép kiểm tra runtime khác nhau |
| “Verifier đã kiểm tra bytecode nên index runtime không thể sai.” | Verifier không biết trước index/object thực tế sẽ nhận lúc chạy |
| “`int[][]` là một khối luôn hình chữ nhật.” | Mảng hai chiều là mảng của mảng; các hàng có thể khác độ dài hoặc `null` |
| “`baload` chỉ dùng cho `byte[]`.” | Nó dùng với cả `byte[]` và `boolean[]` theo JVM Specification |
| “JIT bỏ bounds check thì chương trình không cần tuân theo Java semantics.” | Tối ưu phải bảo toàn hành vi được quan sát theo ngữ nghĩa Java |

## 14. Bài tập tự kiểm tra

**1.** `Object x = null; String s = (String) x;` có ném `ClassCastException` không?  
**Đáp án:** Không. `checkcast` chấp nhận `null`; `s == null`.

**2.** `Object x = Integer.valueOf(10); boolean ok = x instanceof String;` có ném `ClassCastException` không?  
**Đáp án:** Không. `ok == false`. Nếu cố `(String) x`, lúc đó mới phát sinh cast không hợp lệ.

**3.** Với `int[] a = new int[4]`, chỉ số hợp lệ là gì?  
**Đáp án:** 0, 1, 2, 3. `-1` và `4` đều gây `ArrayIndexOutOfBoundsException` khi truy cập.

**4.** Vì sao `int[] a = null; int n = a.length;` gây lỗi?  
**Đáp án:** `arraylength` cần reference tới một đối tượng mảng, nhưng toán hạng là `null`, nên `NullPointerException`.

**5.** `Object[] a = new String[1]; a[0] = Integer.valueOf(1);` lỗi gì?  
**Đáp án:** `ArrayStoreException`: object mảng thực tế là `String[]`, không chứa được một `Integer` như phần tử.

**6.** `instanceof` và `checkcast` khác nhau về operand stack?  
**Đáp án:** `instanceof` lấy reference rồi đẩy `int` 0/1; `checkcast` nếu thành công giữ reference đó trên stack, nếu thất bại ném exception.

**7.** `long[] a = new long[3]; a.length` trả `long` vì phần tử có kiểu `long`?  
**Đáp án:** Không. `arraylength` luôn đẩy **`int`** biểu thị số phần tử; kết quả là 3.

**8.** Vì sao `aastore` cần kiểm tra kiểu động dù `javac` đã kiểm tra phép gán?  
**Đáp án:** Mảng reference **covariant**, vì thế biến kiểu `Object[]` có thể trỏ tới object `String[]`. Compiler chỉ thấy kiểu tĩnh của biến; runtime biết kiểu phần tử thực tế và ngăn phép ghi phá vỡ tính an toàn kiểu.

**9.** `byte[]` và `boolean[]` có hai opcode load hoàn toàn khác nhau?  
**Đáp án:** Không. JVM dùng `baload` cho cả hai trường hợp, với quy tắc biểu diễn/đọc phần tử thích hợp.

**10.** Nếu `ArrayIndexOutOfBoundsException` xuất hiện, có nên sửa bằng cách tắt bytecode verifier không?  
**Đáp án:** Không. Lỗi là do **index runtime** nằm ngoài phạm vi mảng. Cần kiểm tra điều kiện `0 <= index && index < array.length` và logic tạo/duyệt mảng; verifier không phải nguyên nhân.

**Tài liệu đối chiếu:** *Java Virtual Machine Specification*, chương 6.5 (các mục `checkcast`, `instanceof`, `arraylength`, `iaload`, `aaload`, `aastore` và các instruction mảng liên quan): https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-6.html. Với các biểu thức nguồn Java và thứ tự đánh giá toán hạng, xem thêm *Java Language Specification* Java 17.

**Kết luận:** `checkcast` **kiểm tra và giữ reference**, `instanceof` **kiểm tra rồi đẩy `int` 0/1**, `arraylength` **đẩy độ dài kiểu `int` của mảng**, còn `*aload`/`*astore` **đọc/ghi phần tử với kiểm tra null và bounds**; đặc biệt `aastore` còn kiểm tra **runtime component type**. Khi gặp exception, hãy phân biệt **kiểm tra kiểu đối tượng**, **dereference**, **chỉ số**, **tương thích phần tử mảng** và **bytecode verification** trước khi sửa chương trình.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="bytecode-instructions-010">Đọc bytecode biểu thức, vòng lặp và phép gán để dự đoán thứ tự thực thi</a>

<details>
<summary>Xem chi tiết</summary>

# Đọc bytecode biểu thức, vòng lặp và phép gán để dự đoán thứ tự thực thi

## 1. Mục tiêu và cách nhìn đúng về bytecode

Để hiểu một chương trình Java thực hiện hành động nào trước, hành động nào sau, bạn cần kết hợp **quy tắc đánh giá biểu thức của ngôn ngữ Java** với **luồng điều khiển và operand stack của JVM**. Lệnh bytecode không tương ứng một-một với từng dòng mã nguồn: một biểu thức có thể thành nhiều lệnh nạp toán hạng, gọi phương thức, tính toán, sao chép, ghi biến và rẽ nhánh; một vòng lặp thường là tập hợp các nhãn/offset và lệnh nhảy.

Sau bài học, bạn sẽ:

- Biết đọc **javap -c**: offset, opcode, operand, constant-pool reference và điểm đích của lệnh nhảy.
- Theo dõi **local variable slots** và **operand stack** khi thực thi **iload**, **istore**, **iadd**, **imul**, **iinc** và **ireturn**.
- Dự đoán thứ tự của toán hạng, lời gọi phương thức, phép gán, **++i**, **i++**, **+=** và truy cập phần tử mảng.
- Phân tích **if/else**, **for**, **while**, **do-while**, **continue**, **break**, **&&**, **||** và toán tử ba ngôi bằng control-flow graph.
- Phân biệt một thứ tự hành vi **được Java bảo đảm** với một cách phát mã bytecode **do javac lựa chọn**.
- Tránh đọc nhầm thứ tự dòng trong disassembly thành thứ tự mọi dòng được thực thi; khi có nhánh/loop phải lần theo **program counter (PC)** thực tế.

Ví dụ dùng Java 17+; một số mã opcode được trình bày ở dạng **minh họa bytecode tiêu biểu**. Bạn cần chạy **javap** trên chính class file mình biên dịch để biết offset và lệnh chính xác.

## 2. Khung thực thi: frame, locals, operand stack và PC

Mỗi lần gọi phương thức Java, JVM tạo một frame chứa, trong số các thành phần khác, **bảng biến cục bộ (local variables)** và **operand stack**. PC chỉ tới instruction sẽ được thực thi kế tiếp trong luồng bytecode của phương thức đang chạy.

~~~text
            Java bytecode của phương thức
        offset 0 -> lệnh A
        offset 1 -> lệnh B
        offset 4 -> lệnh C       <---- PC có thể nhảy tới đây
        offset 7 -> lệnh D
        offset 8 -> lệnh E
                         |
                         v
Frame đang thực thi:
  Local variables:
    slot 0: tham số / this / biến
    slot 1: biến khác
    ...
  Operand stack:
    top -> giá trị vừa được đẩy
           các giá trị đang chờ được xử lý
  ...
~~~

Quy tắc thao tác cơ bản:

1. Lệnh **load** thường đọc một local slot và **đẩy** giá trị lên operand stack.
2. Lệnh tính toán thường **lấy** các toán hạng từ stack và **đẩy** kết quả trở lại.
3. Lệnh **store** thường **lấy** một giá trị khỏi stack và ghi vào local slot.
4. Lệnh nhánh có điều kiện lấy giá trị cần kiểm tra và quyết định PC đi tới đích nhảy hay instruction tiếp theo.
5. Lệnh gọi method chuẩn bị receiver và arguments theo quy ước JVM; method được gọi tạo frame riêng, sau đó có thể trả kết quả cho caller.
6. **return/ireturn/...** kết thúc frame hiện tại và quay về caller, trừ các trường hợp hoàn tất bất thường qua exception.

Ví dụ mô phỏng ngăn xếp khi thực hiện **(a + b) * 2**, với **a=3, b=4**:

| Instruction minh họa | Locals | Operand stack sau instruction (đáy → đỉnh) |
| --- | --- | --- |
| Trước khi chạy | a=3, b=4 | **[]** |
| **iload_0** | không đổi | **[3]** |
| **iload_1** | không đổi | **[3, 4]** |
| **iadd** | không đổi | **[7]** |
| **iconst_2** | không đổi | **[7, 2]** |
| **imul** | không đổi | **[14]** |
| **ireturn** | hoàn tất method | Trả **14** cho caller |

Bảng chỉ minh họa một phương thức **static** có hai tham số int ở slot 0 và 1. Trong instance method, slot 0 thường là **this**. Các instruction thật có thể khác do tối ưu hằng, cách bố trí locals hoặc khác compiler.

### 2.1. Những opcode hay gặp

| Nhóm | Opcode | Vai trò |
| --- | --- | --- |
| Nạp hằng | **iconst_0**, **iconst_1**, **bipush**, **sipush**, **ldc** | Đẩy hằng lên stack |
| Nạp local | **iload**, **iload_0**... | Đẩy int từ local slot |
| Ghi local | **istore**, **istore_0**... | Lấy int ở stack ghi vào slot |
| Tính toán | **iadd**, **isub**, **imul**, **idiv**, **irem** | Phép toán int, operand lấy từ stack |
| Tăng local | **iinc index, delta** | Cộng hằng delta trực tiếp vào một local int; không lấy/đẩy operand stack |
| Điều kiện một int | **ifeq**, **ifne**, **iflt**, **ifle**, **ifgt**, **ifge** | So sánh với số 0, rẽ nhánh |
| So sánh hai int | **if_icmpeq**, **if_icmpne**, **if_icmpge**... | Lấy hai int, kiểm tra quan hệ |
| Nhảy vô điều kiện | **goto**, **goto_w** | Đổi PC sang một offset |
| Trả về | **ireturn**, **areturn**, **return** | Kết thúc method |
| Sao chép/đổi vị trí | **dup**, **dup_x1**, **dup2**, **swap** | Tổ chức giá trị trên stack, hữu ích trong gán/++ |
| Mảng | **iaload**, **iastore**, **arraylength** | Đọc, ghi và lấy độ dài mảng |
| Field | **getfield**, **putfield**, **getstatic**, **putstatic** | Đọc/ghi trường instance/static |
| Gọi method | **invokestatic**, **invokevirtual**, **invokeinterface**, **invokespecial**, **invokedynamic** | Gọi hoặc liên kết thao tác động |

Chữ **i** chỉ kiểu tính toán **int** ở JVM; source **byte**, **short**, **char**, **boolean** đôi khi cũng được xử lý thông qua các opcode int. **long/double** dùng hai units trong mô hình frame và có opcode riêng.

## 3. Đọc đầu ra javap -c mà không nhầm offset

Cú pháp thông dụng:

~~~bash
javac --release 17 FileName.java
javap -c -p FileName
~~~

Ký hiệu mẫu:

~~~text
  0: iload_0
  1: iload_1
  2: iadd
  3: ireturn
~~~

- **0, 1, 2, 3** là **bytecode offsets**, không phải số dòng source.
- **iload_0** có thể là một instruction dài 1 byte; instruction có operand có thể dài hơn.
- Một lệnh như **6: if_icmpge 19** nghĩa là nếu điều kiện đúng, PC tới offset **19** trong cùng method; nếu sai, đi tiếp instruction kế tiếp. Số **19** trong javap được in là target **offset đích**, không phải giá trị boolean.
- **#n** sau các opcode như **invokevirtual #n**, **getfield #n** là chỉ số tham chiếu constant pool, không phải địa chỉ mã máy hay giá trị đang chạy.
- Phần chú giải sau **//** thường diễn giải constant pool reference, method descriptor hoặc type. Chúng giúp tìm đúng method/field được gọi.

Khi có vòng lặp, thứ tự các dòng xuất hiện trong javap là **thứ tự lưu lệnh**, không phải chuỗi hành động chỉ đi qua một lần. Phải đi theo PC và mọi branch target, kể cả **goto** nhảy ngược.

### 3.1. Gắn kết với source code

Dùng thêm:

~~~bash
javap -c -p -l FileName
javap -v -p FileName
~~~

- **-l** hiển thị thông tin line/local-variable tables **nếu có**; không phải class file nào cũng kèm đủ debug metadata.
- **-v** hiển thị constant pool, Code attributes, StackMapTable và những chi tiết khác để kiểm tra.
- **-p** cho phép xem cả thành viên private.

Không nên khẳng định mọi statement Java tạo một dải bytecode riêng hoặc số dòng source tăng cùng PC. Compiler có thể gộp hằng, tạo synthetic methods, tối ưu nhánh không cần thiết, thay cách lặp và thay đổi thứ tự lưu lệnh miễn bảo toàn ngữ nghĩa Java.

## 4. Đánh giá biểu thức từ trái sang phải: thứ tự khác độ ưu tiên

**Java Language Specification (JLS §15.7)** bảo đảm toán hạng của nhiều biểu thức được đánh giá **từ trái sang phải** theo quy tắc từng dạng biểu thức. Điều này cần phân biệt với **precedence** (độ ưu tiên toán tử) và **associativity** (cách nhóm biểu thức).

Ví dụ:

~~~java
// Minh họa bên trong một phương thức Java.
int result = left() + right() * 4;
~~~

**right() * 4** được nhóm với phép nhân vì phép nhân có độ ưu tiên cao hơn phép cộng, **nhưng left() vẫn được gọi trước right()** do left operand của phép cộng được đánh giá trước right operand.

Một thứ tự hành động hợp lệ:

~~~text
1. Gọi left() và lấy kết quả.
2. Gọi right() và lấy kết quả.
3. Nhân giá trị trả về của right() với 4.
4. Cộng kết quả của left() với tích vừa có.
5. Ghi tổng vào result.
~~~

Compiler có thể dùng operand stack để giữ kết quả **left()** trong khi tiếp tục tính vế phải.

**Không được kết luận:** “Nhân ưu tiên cao nên side effect trong right() phải xảy ra trước side effect trong left().” Ưu tiên quyết định **cấu trúc phép toán**, còn evaluation order quyết định **thời điểm đánh giá**.

### 4.1. Tham số method cũng đánh giá theo thứ tự

~~~java
// Ví dụ minh họa:
combine(first(), second(), third());
~~~

Trong Java, method receiver expression (nếu có) được xử lý theo quy tắc lời gọi, sau đó các argument expressions được đánh giá từ trái sang phải. Nếu **first()** ném exception thì **second()** và **third()** không tiếp tục được đánh giá; không phải compiler tùy ý đổi chúng để tối ưu.

Lúc đọc bytecode, hãy theo dõi các **invoke...** và vị trí kết quả được đẩy lên stack. Bước gọi **combine** thực sự diễn ra khi các argument cần thiết đã được đánh giá xong.

### 4.2. Ngoại lệ và side effect là bằng chứng của thứ tự

Ví dụ minh họa:

~~~java
// Minh họa, không phải chương trình độc lập:
int value = 10 / divisor() + logAndReturn(3);
~~~

Nếu **divisor()** trả **0**, instruction chia int ném **ArithmeticException** trước khi Java đánh giá toán hạng **logAndReturn(3)** phía sau phép cộng. Vì vậy side effect của **logAndReturn** không xảy ra.

Một trình tối ưu/JIT có thể thay cách triển khai nội bộ, nhưng kết quả có thể quan sát phải giữ quy tắc Java như các side effects/exceptions được yêu cầu. Không suy từ một đoạn disassembly thiếu side effects rằng Java không có thứ tự đánh giá.

## 5. Ví dụ runnable số 1: quan sát thứ tự bằng console

Tạo tệp **BytecodeOrderDemo.java**:

~~~java
public class BytecodeOrderDemo {
    static int next(String name, int value) {
        System.out.println("eval:" + name);
        return value;
    }

    static boolean left() {
        System.out.println("check:left");
        return false;
    }

    static boolean right() {
        System.out.println("check:right");
        return true;
    }

    public static void main(String[] args) {
        int expression = next("A", 2) + next("B", 3) * 4;
        System.out.println("expr=" + expression);

        int x = 4;
        int assigned = (x = 7) + x++;
        System.out.println("assign:result=" + assigned + ",x=" + x);

        int p = 2;
        int increments = ++p + p++;
        System.out.println("inc:both=" + increments + ",p=" + p);

        boolean shortCircuit = left() && right();
        System.out.println("and=" + shortCircuit);

        boolean eagerAnd = left() & right();
        System.out.println("eagerAnd=" + eagerAnd);

        int[] array = {10, 20};
        int index = 0;
        array[index++] += next("delta", 3);
        System.out.println("array:first=" + array[0] + ",index=" + index);

        int sum = 0;
        for (int i = 0; i < 4; i++) {
            if (i == 1) {
                continue;
            }
            if (i == 3) {
                break;
            }
            sum += i;
        }
        System.out.println("loop=" + sum);
    }
}
~~~

Biên dịch và chạy bằng JDK 17+:

~~~bash
javac --release 17 -encoding UTF-8 BytecodeOrderDemo.java
java BytecodeOrderDemo
~~~

**Stdout chính xác:**

~~~text
eval:A
eval:B
expr=14
assign:result=14,x=8
inc:both=6,p=4
check:left
and=false
check:left
check:right
eagerAnd=false
eval:delta
array:first=13,index=1
loop=2
~~~

Vì sao thứ tự như vậy?

1. **next("A",2)** được gọi trước **next("B",3)** dù phép nhân ưu tiên hơn phép cộng. Tổng là **2 + 3 * 4 = 14**.
2. **(x = 7)** gán **x** thành 7 và biểu thức gán có giá trị 7. **x++** trả giá trị cũ 7 rồi tăng x thành 8; **assigned = 7 + 7 = 14**.
3. **++p** tăng p từ 2 lên 3 rồi trả 3; **p++** trả 3 rồi tăng p lên 4; biểu thức tổng là **6**.
4. **left() && right()** dừng khi left trả false nên **right() không chạy**.
5. **left() & right()** khi áp dụng cho boolean vẫn đánh giá **cả hai vế**, kết quả là false.
6. **array[index++] += next("delta",3)** lấy phần tử tại chỉ số cũ 0, ghi nhớ giá trị hiện tại là 10, đánh giá delta = 3 rồi ghi 13 vào **array[0]**; index trở thành 1.
7. Vòng for đi qua i=0 (cộng 0), i=1 (continue rồi cập nhật i), i=2 (cộng 2), i=3 (break). Kết quả **sum=2**.

Ví dụ cố ý dùng các biểu thức chứa side effect để nhìn rõ evaluation order. Trong code nghiệp vụ thực tế, tách side effect thành các dòng riêng thường giúp bảo trì và tránh nhầm hơn.

## 6. Phép gán đơn: lưu vào local, field hay array

Xét:

~~~java
// Ví dụ minh họa:
int a = 2;
int b = 5;
a = b + 3;
~~~

Một cách tổ chức bytecode tiêu biểu:

~~~text
... push 2; store a
... push 5; store b

load b
push 3
iadd
store a
~~~

Điểm quan trọng là **b + 3** được tính trước khi giá trị mới được ghi vào **a**. Phép gán trong Java là một **expression**: sau khi thực hiện, nó có giá trị dùng được bởi biểu thức bao ngoài.

Ví dụ **int z = (a = 7) + a;**: phép gán bên trái xảy ra trước khi đọc **a** bên phải, nên cả hai toán hạng đều bằng 7 (nếu không có tác động ngoài luồng đang xét), kết quả 14.

### 6.1. Local variable và operand stack là hai nơi khác nhau

Đừng nhầm **istore_1** là “đưa giá trị lên stack”: opcode này lấy giá trị đang ở stack rồi **lưu xuống** local slot 1. **iload_1** làm chiều ngược lại. Thứ tự toán hạng của **isub/idiv** đặc biệt quan trọng: stack **[a, b]** (b trên đỉnh) rồi **isub** cho **a - b**, không phải **b - a**.

Ví dụ stack:

~~~text
iconst_3     -> [3]
iconst_2     -> [3, 2]
isub         -> [1]
~~~

### 6.2. Gán có thể cần giữ giá trị kết quả

Nếu giá trị gán còn được dùng, ví dụ:

~~~java
// Minh họa:
int a;
int b;
b = (a = 5);
~~~

Compiler có thể dùng **dup** hoặc một cách tổ chức stack tương đương để vừa lưu vào **a** vừa giữ lại giá trị 5 cho phép gán **b**. Nếu gán là statement độc lập, không cần giữ giá trị sau khi store.

Chính vì vậy những lệnh **dup**, **dup_x1**, **dup_x2** thường xuất hiện cạnh phép gán field/array, biểu thức tăng và các thao tác vừa ghi vừa cần trả một giá trị. Không nên suy ngược rằng cứ có **dup** là source đã viết một phép gán cụ thể nào đó.

## 7. Prefix ++i, postfix i++ và iinc

Với biến local kiểu **int**:

~~~java
int i = 3;
int oldValue = i++; // oldValue = 3, i = 4
int newValue = ++i; // newValue = 5, i = 5
~~~

Khác biệt là **giá trị của biểu thức**, không chỉ khác thời điểm cập nhật:

| Biểu thức | Giá trị biến sau thao tác | Giá trị biểu thức |
| --- | ---: | ---: |
| **i++**, trước đó i=3 | 4 | 3 |
| **++i**, trước đó i=3 | 4 | 4 |
| **i--**, trước đó i=3 | 2 | 3 |
| **--i**, trước đó i=3 | 2 | 2 |

**iinc index, delta** là instruction JVM tăng một local int mà không cần đi qua operand stack. Nếu giá trị biểu thức **i++** cần được giữ, compiler có thể **iload** giá trị cũ trước **iinc**; nếu là **++i**, có thể **iinc** trước rồi **iload** giá trị mới.

Ví dụ bytecode **minh họa**, không bảo đảm mọi javac chọn đúng dãy:

~~~text
i++ khi cần lấy giá trị cũ:
    iload <slot>
    iinc <slot>, 1

++i khi cần lấy giá trị mới:
    iinc <slot>, 1
    iload <slot>
~~~

Nếu chỉ viết **i++;** làm statement, giá trị cũ không được dùng, compiler có thể chỉ sinh **iinc**. Với field, array component, kiểu **long**, hoặc các biểu thức có side effects phức tạp, không thể đơn giản áp dụng **iinc** cho mọi trường hợp; bytecode có thể cần **getfield/putfield**, **iaload/iastore**, **dup** và các opcode kiểu phù hợp.

### 7.1. ++ không đồng nhất với toán tử += ở mọi trường hợp

Ở Java source, **i++** có quy tắc trả giá trị cũ, **++i** trả giá trị mới; **i += 1** là compound assignment trả giá trị đã cập nhật. Với local **int** không có side effect khác, một compiler có thể dùng cùng **iinc** làm bước cập nhật, nhưng khi biểu thức cần có giá trị thì phải giữ đúng cách nạp trước/sau.

Ví dụ **int r = i++ + ++i;** với **i = 1**:

~~~text
vế trái i++  -> trả 1, i thành 2
vế phải ++i  -> i thành 3, trả 3
r = 1 + 3 = 4, i cuối = 3
~~~

Đây là Java hợp lệ và có thứ tự đánh giá xác định, dù cách viết phụ thuộc nhiều side effects rất dễ gây nhầm khi review code.

## 8. Phép gán kết hợp += và thứ tự đánh giá bên trái

Phép **x += expr** có tác dụng giống **x = (T) (x + expr)** đối với kiểu T của x, **ngoại trừ** biểu thức xác định biến ở vế trái chỉ được đánh giá **một lần** và giá trị cũ được đọc/ghi nhớ theo ngữ nghĩa compound assignment.

Ví dụ:

~~~java
// Minh họa trong một phương thức:
int x = 10;
x += 3; // x = 13
~~~

Với local **int** và hằng số phù hợp, javac có thể biên dịch kiểu **iinc**, thay vì bắt buộc **iload; iconst; iadd; istore**. Vì vậy nên so **hành vi** và stack/local effect, không ép mọi phép += thành bốn instruction cố định.

### 8.1. Trường hợp ++ bên chỉ số của mảng

~~~java
// Minh họa:
int[] values = {10, 20};
int k = 0;
values[k++] += 3;
~~~

Hành vi:

1. Xác định array reference **values** và index expression **k++** (giá trị index cũ = 0, sau đó k=1).
2. Kiểm tra truy cập phần tử thích hợp và lấy **values[0] = 10**.
3. Đánh giá RHS **3**; cộng thành 13.
4. Ghi lại **values[0] = 13**.

Kết quả **values[0] = 13** và **k = 1**. **index expression chỉ được đánh giá một lần**, không phải hai lần như khi viết một biểu thức thay thế cẩu thả:

~~~java
// KHÔNG tương đương, vì k++ bị chạy hai lần:
values[k++] = values[k++] + 3;
~~~

Mẫu bytecode của compound array assignment thường phải giữ reference, index và old value trên stack qua các lệnh như **dup2**, **iaload**, **iadd**, **iastore**. Dãy lệnh chính xác thay đổi theo kiểu mảng và compiler.

### 8.2. Tại sao += trên byte có thể dùng i2b?

~~~java
byte b = 127;
b += 1;        // Hợp lệ; b trở thành -128
// b = b + 1;  // Compile-time error: b + 1 có kiểu int
~~~

Ngôn ngữ Java cho phép compound assignment với bước thu hẹp về kiểu đích. JVM có thể tính toán qua int rồi dùng **i2b** để đưa kết quả về ngữ nghĩa byte. Không nên cho rằng mọi biến byte được cộng trực tiếp bằng một instruction “byte add”; opcode **iadd** vận hành theo int computational type.

## 9. Field và array assignment: nhận diện thứ tự side effects

### 9.1. Gán field của object

Ví dụ:

~~~java
// Minh họa trong class có field int value:
holder().value = compute();
~~~

Java phải đánh giá biểu thức xác định receiver (ở đây **holder()**) trước RHS, với những ràng buộc kiểm tra và gán của field access. Bytecode có thể có các lệnh gọi **holder**, **compute** và **putfield**. Nếu receiver null, hiệu ứng và thời điểm ném **NullPointerException** cần xét theo quy tắc Java của dạng assignment, không chỉ dựa vào tên opcode cuối.

Nếu có **holder().value += compute()**, compiler phải bảo toàn giá trị field cũ và danh tính receiver được đánh giá một lần; **getfield** và **putfield** có thể xuất hiện.

### 9.2. Gán array và kiểm tra chỉ số

Đặc tả Java phân biệt thứ tự kiểm tra ở **simple assignment** và **compound assignment** đến array component.

- Với **array[index] = rhs()** (simple assignment), Java đánh giá array reference và index, rồi đánh giá RHS trước khi thực hiện bước lưu vào array và các kiểm tra runtime gắn với bước gán theo quy tắc tương ứng.
- Với **array[index] += rhs()** (compound assignment), sau khi đánh giá array reference/index, Java thực hiện kiểm tra null/bounds và đọc giá trị cũ của phần tử **trước khi** đánh giá RHS.

Đây là khác biệt có thể quan sát nếu RHS có side effect hoặc ném exception. Không được suy rằng cả hai luôn gọi RHS trước/sau **ArrayIndexOutOfBoundsException** theo một thứ tự giống nhau.

Để phân tích một chương trình cụ thể:

1. Xác định đây là **simple** hay **compound** assignment.
2. Theo dõi array reference và index expressions theo thứ tự.
3. Đánh dấu thời điểm thực hiện null/bounds check và đọc phần tử nếu có.
4. Theo dõi RHS, phép toán và lệnh ghi mảng **xastore** tương ứng.

**Thực hành hữu ích:** biên dịch một ví dụ nhỏ có logging ở index/RHS, rồi dùng **javap -c** xác định **iaload/iastore** và các lời gọi. Đây là trường hợp cần bám ngữ nghĩa JLS, tránh suy diễn chỉ bằng vẻ ngoài của mã nguồn.

## 10. Rẽ nhánh if/else: theo từng cạnh của control-flow graph

Một **if** thường được biến thành **so sánh + nhảy có điều kiện + nhảy vô điều kiện** nếu có else. Ví dụ:

~~~java
static int absLike(int value) {
    if (value >= 0) {
        return value;
    } else {
        return -value;
    }
}
~~~

Một layout bytecode minh họa:

~~~text
  iload value
  iflt NEGATIVE
  iload value
  ireturn
NEGATIVE:
  iload value
  ineg
  ireturn
~~~

Ở JVM, **iflt** lấy một int khỏi stack rồi kiểm tra **value < 0**. Nếu true, nó nhảy đến nhánh NEGATIVE; nếu false, thực thi tiếp phần trả value. Đây là cách diễn giải cùng ngữ nghĩa **if (value >= 0)** mà branch được viết trên trường hợp phủ định.

**Đừng đọc nhầm:** source có dấu **>=**, nhưng opcode có thể là **iflt** để nhảy qua nhánh true. Khi đọc bytecode, phải xem **opcode + target + fall-through path** như một chỉnh thể.

### 10.1. ifeq/ifne cho boolean

Với:

~~~java
// Minh họa:
if (ready) {
    act();
}
~~~

Về mặt computational JVM, **ready** có thể được biểu diễn bằng int, rồi compiler dùng **ifeq** để nhảy qua **act()** khi giá trị false (0). Không phải **ready** ở Java trở thành kiểu số có thể cộng trừ; nó vẫn là boolean theo ngôn ngữ.

### 10.2. if_icmpge khác ifge

- **ifge** lấy **một int** và so nó với 0.
- **if_icmpge** lấy **hai int** và kiểm tra giá trị thứ nhất lớn hơn hoặc bằng giá trị thứ hai.
- **if_acmpeq/if_acmpne** kiểm tra identity/equality của hai reference, không thực thi **equals(...)**.
- **ifnull/ifnonnull** kiểm tra null của một reference.

Khi phân tích điều kiện **i < limit**, bạn có thể gặp **if_icmpge** để nhảy thoát vòng lặp khi điều kiện **không** còn đúng.

## 11. Đọc vòng for: khởi tạo, kiểm tra, thân, cập nhật

Mã nguồn:

~~~java
static int sumBefore(int limit) {
    int sum = 0;
    for (int i = 0; i < limit; i++) {
        sum += i;
    }
    return sum;
}
~~~

Control flow khái quát:

~~~text
START
  |
  v
sum = 0, i = 0
  |
  v
CHECK: i < limit? --- false ---> EXIT: return sum
  |
 true
  v
BODY: sum += i
  |
  v
UPDATE: i++
  |
  +------------------------> CHECK
~~~

Bytecode minh họa với locals **slot 0=limit, slot 1=sum, slot 2=i** (offset thực có thể phụ thuộc compiler):

~~~text
 0: iconst_0
 1: istore_1              // sum = 0
 2: iconst_0
 3: istore_2              // i = 0
 4: iload_2               // CHECK: push i
 5: iload_0               // push limit
 6: if_icmpge 19          // i >= limit -> EXIT
 9: iload_1               // push sum
10: iload_2               // push i
11: iadd                  // sum + i
12: istore_1              // sum = ...
13: iinc 2, 1             // i++
16: goto 4                // loop back to CHECK
19: iload_1
20: ireturn
~~~

Lần theo **sumBefore(4)**:

| Lượt kiểm tra | i | i < 4? | sum trước body | Hoạt động |
| --- | ---: | --- | ---: | --- |
| 1 | 0 | true | 0 | Cộng 0; i thành 1 |
| 2 | 1 | true | 0 | Cộng 1; i thành 2 |
| 3 | 2 | true | 1 | Cộng 2; i thành 3 |
| 4 | 3 | true | 3 | Cộng 3; i thành 4 |
| 5 | 4 | false | 6 | Nhảy đến ireturn |

Giá trị trả về là **6**. Mũi tên **goto 4** mới là lý do nhóm lệnh CHECK/BODY/UPDATE chạy lặp; nếu chỉ đọc từ offset nhỏ đến offset lớn một lần, bạn sẽ dự đoán sai.

### 11.1. for và while thường là cùng một ý tưởng ở bytecode

Với ngữ nghĩa tương đương:

~~~java
int i = 0;
while (i < limit) {
    sum += i;
    i++;
}
~~~

Compiler có thể phát sinh luồng điều khiển gần giống **for**, vì cả hai đều kiểm tra điều kiện trước khi chạy body. **for** chỉ gom initialization, condition, update vào một cú pháp.

Tuy vậy, **scope** của biến **i**, vị trí update và đường đi của **continue** cần được giữ theo đúng ngôn ngữ. Không phải cứ viết lại for thành while theo mắt thường là tương đương trong mọi trường hợp.

## 12. while, do-while, continue và break

### 12.1. while kiểm tra trước, do-while kiểm tra sau

~~~java
int a = 0;
while (a < 0) {
    a++;
}

int b = 0;
do {
    b++;
} while (b < 0);
~~~

Sau hai vòng: **a = 0**, **b = 1**. **while** không chạy body lần nào vì điều kiện false ngay lần đầu; **do-while** chạy body ít nhất một lần rồi mới xét điều kiện.

Sơ đồ:

~~~text
while:
  CHECK? ---- false ----> EXIT
    |
   true
    v
   BODY ----------------> CHECK

do-while:
  BODY
    |
    v
  CHECK? ---- false ----> EXIT
    |
   true
    +-------------------> BODY
~~~

Một compiler có thể tối ưu nhánh nếu biết điều kiện là hằng, nên hãy dự đoán theo semantics trước, sau đó đối chiếu bytecode thực tế.

### 12.2. continue trong for nhảy tới UPDATE, rồi mới CHECK

Với:

~~~java
for (int i = 0; i < 4; i++) {
    if (i == 1) continue;
    System.out.println(i);
}
~~~

Output là **0, 2, 3** trên các dòng riêng. Khi i=1, **continue** bỏ phần còn lại của thân vòng lặp, nhưng vẫn thực hiện **i++** của for trước lần CHECK tiếp theo. Trong bytecode, cạnh **continue** có thể nhảy tới block **UPDATE**.

Ở **while**, continue thường đi đến **CHECK** của vòng bao chứa; nếu tự chuyển code for sang while mà đặt update ở cuối body, **continue** có thể bỏ mất update và gây lặp vô hạn.

### 12.3. break thoát vòng đang được chọn

**break** không nhảy về CHECK; nó đi tới **điểm sau vòng lặp** (hoặc target được chỉ định với labeled break). Với hai vòng lồng nhau, **break** không nhãn thoát vòng trong cùng; **break outer;** có thể thoát vòng gắn label **outer**.

**Labeled continue** nhảy sang chu kỳ tiếp theo của vòng lặp được gắn label tương ứng (với các yêu cầu đúng cú pháp JLS). Khi đọc bytecode không thấy “tên label Java” một cách tất yếu; chỉ thấy các target offsets đã được compiler tính.

### 12.4. finally có thể làm đường đi phức tạp hơn

Nếu **break**, **continue** hoặc **return** nằm trong một vùng **try/finally**, Java vẫn phải thực hiện các bước **finally** thích hợp khi rời vùng, trừ các trường hợp đặc biệt theo đặc tả. Compiler có thể sinh thêm control-flow và exception handlers. Khi có try/finally, không nên giải nghĩa một **goto** đơn lẻ như toàn bộ hành vi của break/continue mà bỏ qua các block cleanup.

## 13. &&, || và &: nhìn nhánh để thấy short-circuit

Toán tử logic Java:

| Toán tử | Đánh giá RHS khi nào? | Kết quả |
| --- | --- | --- |
| **a && b** | Chỉ khi a là true | AND logic có short circuit |
| **a || b** | Chỉ khi a là false | OR logic có short circuit |
| **a & b** với boolean | Luôn đánh giá cả hai vế | AND logic không short circuit |
| **a | b** với boolean | Luôn đánh giá cả hai vế | OR logic không short circuit |
| **a ^ b** với boolean | Luôn đánh giá cả hai vế | XOR logic |

Ví dụ:

~~~java
// Minh họa:
if (isReady() && loadData()) {
    process();
}
~~~

Một control-flow tương đương:

~~~text
call isReady()
  if false -> END
call loadData()
  if false -> END
call process()
END:
~~~

Nếu **isReady()** trả false, lệnh gọi **loadData()** phải bị bỏ qua. Compiler thường hiện thực bằng instruction kiểu **ifeq/ifne**; không thể thay tùy tiện **&&** bằng **iand** sau khi đã đánh giá hai vế vì điều đó có thể làm RHS chạy trái ngữ nghĩa source.

Trong biểu thức không short circuit như **isReady() & loadData()**, hai bên đều được đánh giá (trái rồi phải). Compiler có thể sử dụng **iand** cho giá trị boolean tính toán qua int, hoặc các lệnh tương đương khác, miễn kết quả và side effects được bảo toàn.

**Tránh một lỗi nguy hiểm:** trong điều kiện **obj != null && obj.isActive()**, toán tử **&&** bảo vệ việc gọi method trên null. Đổi thành **&** có thể gây **NullPointerException**.

## 14. Toán tử ba ngôi và switch

### 14.1. condition ? trueValue : falseValue

~~~java
int result = score >= 60 ? passValue() : failValue();
~~~

Java đánh giá **score >= 60**, sau đó chỉ đánh giá **một trong hai nhánh**. Bytecode có thể dùng một lệnh điều kiện để nhảy tới block tương ứng, rồi **goto** gộp về điểm chung, ví dụ:

~~~text
evaluate condition
if false -> ELSE
call passValue
goto JOIN
ELSE:
call failValue
JOIN:
store result
~~~

Hai nhánh không được gọi cùng lúc để “rồi chọn kết quả”; side effects/exception phải theo đúng một nhánh được chọn.

### 14.2. switch có nhiều cách phát mã

Với **switch** trên giá trị kiểu int hoặc các dạng tương ứng, bytecode có thể có **tableswitch** hoặc **lookupswitch**:

- **tableswitch** hữu ích cho dải giá trị gần liên tiếp, có bảng offset nhảy.
- **lookupswitch** dùng các cặp match-value và offset target.
- Với string switch, enum switch hoặc pattern/switch expressions, compiler có thể sinh các bước xử lý bổ sung.

Cần đọc chính instruction **tableswitch/lookupswitch** và mọi target block, không đoán thứ tự chạy case theo thứ tự case xuất hiện trong disassembly. **break** trong switch và **break** trong vòng lặp có target khác nhau theo cấu trúc điều khiển.

## 15. Ví dụ runnable số 2: bytecode gọn để đọc từng lệnh

Tạo tệp **BytecodeLoopLab.java**:

~~~java
public class BytecodeLoopLab {
    static int multiplyAdd(int a, int b) {
        return (a + b) * 2;
    }

    static int sumBefore(int limit) {
        int sum = 0;
        for (int i = 0; i < limit; i++) {
            sum += i;
        }
        return sum;
    }

    static int sign(int value) {
        return value > 0 ? 1 : -1;
    }

    public static void main(String[] args) {
        System.out.println("multiplyAdd=" + multiplyAdd(2, 3));
        System.out.println("sumBefore=" + sumBefore(4));
        System.out.println("signZero=" + sign(0));
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac --release 17 -encoding UTF-8 BytecodeLoopLab.java
java BytecodeLoopLab
javap -c -p BytecodeLoopLab
~~~

**Stdout chính xác của lệnh java (không gồm phần javap):**

~~~text
multiplyAdd=10
sumBefore=6
signZero=-1
~~~

Với **multiplyAdd**, một trình biên dịch **javac** phổ biến tạo dạng:

~~~text
0: iload_0
1: iload_1
2: iadd
3: iconst_2
4: imul
5: ireturn
~~~

Lần theo các trạng thái operand stack:

| Offset | Instruction | Stack sau lệnh |
| ---: | --- | --- |
| 0 | iload_0 | [2] |
| 1 | iload_1 | [2, 3] |
| 2 | iadd | [5] |
| 3 | iconst_2 | [5, 2] |
| 4 | imul | [10] |
| 5 | ireturn | trả 10 |

Đây là bytecode mẫu rất phù hợp để luyện đọc stack vì không có side effects ngoài và không có branch.

Với **sumBefore**, hãy đối chiếu dãy lệnh **iload**, **if_icmpge**, **iadd**, **istore**, **iinc**, **goto** đã trình bày ở mục 11. Với **sign**, tìm nhánh kiểu **ifle** tới trường hợp zero/negative, lệnh nạp 1 hay -1 và chỗ join/return. Compiler có thể phát sinh:

~~~text
load value
if value <= 0 -> NEGATIVE
push 1
goto RETURN
NEGATIVE:
push -1
RETURN:
ireturn
~~~

Điều đáng học là **hai đường đi khác nhau hội tụ tại một điểm trả về**, không phải học thuộc offset cố định của compiler.

## 16. Phân tích một trường hợp biểu thức khó bằng bảng trạng thái

Xét:

~~~java
// Minh họa bên trong một phương thức:
int i = 1;
int value = i++ + ++i * i;
~~~

Các bước theo JLS:

| Bước | Hành động | i sau bước | Giá trị toán hạng được giữ |
| --- | --- | ---: | --- |
| 1 | Đánh giá vế trái của dấu +: **i++** | 2 | Vế trái **+** giữ **1** |
| 2 | Đánh giá vế trái của dấu *: **++i** | 3 | Vế trái **\*** giữ **3** |
| 3 | Đánh giá vế phải dấu *: đọc **i** | 3 | Vế phải **\*** là **3** |
| 4 | Tính phép nhân **3 * 3** | 3 | Kết quả nhân **9** |
| 5 | Tính phép cộng **1 + 9** | 3 | **value = 10** |

Vì vậy kết quả **value = 10**, **i = 3**. Một cách nhìn stack ở mức ý tưởng:

~~~text
i++     -> push 1, i thành 2          stack [1]
++i     -> push 3, i thành 3          stack [1, 3]
i       -> push 3                     stack [1, 3, 3]
imul    -> push 9 thay 3,3            stack [1, 9]
iadd    -> push 10 thay 1,9           stack [10]
istore  -> value = 10                 stack []
~~~

Dù biểu thức hợp lệ và có kết quả xác định, dạng viết nhiều thay đổi trên cùng một biến là kém rõ ràng cho code production. Phân tích bytecode giúp xác nhận quy tắc, còn viết code dễ đọc giúp tránh phải phân tích những trường hợp này khi sửa lỗi.

## 17. Trường hợp thứ tự đánh giá và tối ưu compiler

### 17.1. Không có ánh xạ một-một từ dòng Java sang lệnh JVM

Đoạn source:

~~~java
int answer = 10 + 20;
~~~

Có thể được compiler gộp thành một hằng **30** thay vì bytecode lần lượt đẩy 10, đẩy 20 rồi **iadd**. Nếu mã không có side effects, giữ nguyên kết quả là đủ trong phạm vi tối ưu hợp lệ.

Ngược lại:

~~~java
int answer = next("A", 10) + next("B", 20);
~~~

Không thể tùy ý hoán đổi hai lời gọi khi chúng in ra những dòng khác nhau; side effects phải phản ánh evaluation order của Java.

### 17.2. Javac bytecode và JIT machine code là hai tầng

**javap -c** đọc **bytecode trong class file**. JVM có thể interpret hoặc JIT compile các method nóng sang mã máy, tối ưu nội bộ, inline method, giữ giá trị trong thanh ghi hoặc xóa thao tác không ảnh hưởng hành vi quan sát. Vì vậy:

- Dùng **javap** để hiểu định dạng và control flow của bytecode **đã biên dịch**.
- Dùng kết quả chạy và đặc tả Java để kiểm tra **ngữ nghĩa quan sát được**.
- Không dùng javap như hình ảnh chính xác của các lệnh CPU mà JIT đang chạy.
- Không coi một dạng bytecode của riêng javac phiên bản X là quy tắc bắt buộc của JLS.

### 17.3. Lỗi runtime thay đổi đường đi

Các instructions như **idiv**, **iaload**, **getfield** hoặc lời gọi method có thể ném exception. Khi đó PC không đơn giản đi tiếp offset kế tiếp; JVM tìm exception handler thích hợp theo exception table, hoặc tiếp tục unwind frame. Vì vậy “đọc thẳng từ trên xuống” có thể sai kể cả khi không thấy lệnh **goto** gần đó.

Nếu có **finally**, bytecode có thể phức tạp hơn do cleanup logic. Để dự đoán thứ tự thực thi, phải xét **mọi cạnh thường lẫn cạnh exception**.

### 17.4. Không dùng thứ tự bytecode để dự đoán lịch chạy giữa threads

JLS cho thứ tự đánh giá biểu thức của **một thread** theo ngữ nghĩa chương trình. Bytecode của một method không chứng minh thứ tự xen kẽ giữa nhiều threads là cố định. Muốn suy luận kết quả concurrent code phải xét **Java Memory Model**, đồng bộ hóa và quan hệ happens-before. Tránh suy từ PC của thread A rằng thread B sẽ chờ A đến một offset nhất định.

## 18. Quy trình đọc bytecode để dự đoán thứ tự chính xác

Thay vì thử đoán cả method, thực hiện theo vòng lặp phân tích sau:

1. **Biên dịch mã nguồn đúng phiên bản**, lưu class file và dùng **javap -c -p** để lấy instruction offsets.
2. **Xác định method descriptor** và những local slots đầu tiên dành cho **this**/tham số; hiểu kiểu giá trị (int, long, reference...).
3. **Đánh dấu các nguồn side effect:** **invoke...**, **putfield/putstatic**, **xastore**, I/O, phương thức tự tăng, monitor và những thao tác có thể ném exception.
4. **Lập bảng** gồm **PC, local variables, operand stack, nhánh được chọn, output/side effects**. Khởi tạo trạng thái từ input thực tế.
5. **Mô phỏng từng instruction**: đọc/ghi stack đúng thứ tự; chú ý toán tử không giao hoán như trừ, chia; cập nhật locals khi gặp store hoặc iinc.
6. **Đối với if/loop**, tính điều kiện rồi chuyển PC sang target hoặc fall-through; không đi qua cả hai nhánh.
7. **Với gán compound/++**, đánh dấu old value, new value và số lần đánh giá biểu thức receiver/index; đừng nhầm stack value với giá trị variable hiện tại.
8. **Theo dõi exceptions** khi cần: thao tác gây lỗi có thể hủy việc đánh giá các biểu thức phía sau hoặc chuyển vào handler.
9. **Ghi stdout theo đúng thời điểm gọi println**, sau khi biểu thức argument của nó đã được đánh giá.
10. **Đối chiếu chương trình chạy**, cuối cùng giải thích bằng **quy tắc JLS** và bytecode quan sát được. Nếu khác, kiểm tra lớp biên dịch, JDK, input, thread/concurrency và các side effects bỏ sót.

Bảng sườn để tự điền:

| PC | Instruction | Locals trước/sau | Stack trước/sau | Branch target | Side effect/output |
| --- | --- | --- | --- | --- | --- |
| offset hiện tại | opcode | giá trị thay đổi? | lấy/đẩy phần tử gì? | nhảy hay fall-through? | in, ghi field/array, gọi method? |

Bảng này đặc biệt hiệu quả với code gồm các lệnh **iinc** và các nhánh nhảy ngược: sau mỗi lần **goto**, bạn quay về đúng PC mục tiêu với local variables đã được cập nhật.

## 19. Những nhầm lẫn phổ biến

1. **“Lệnh hiện ở dòng dưới javap chắc chắn chạy sau lệnh dòng trên.”** Không đúng nếu điều khiển nhảy qua hoặc lặp lại instruction.
2. **“Độ ưu tiên toán tử là thứ tự gọi method.”** Không đúng; **left() + right()*4** vẫn đánh giá left() trước right().
3. **“i++ và ++i là như nhau vì đều tăng i.”** Sai khi giá trị biểu thức được dùng: postfix trả cũ, prefix trả mới.
4. **“iinc đẩy giá trị mới lên stack.”** Không; iinc trực tiếp thay local, không có operand-stack effect như iload.
5. **“istore đọc local.”** Ngược lại; **iload** đọc local rồi push, **istore** pop rồi ghi local.
6. **“&& chỉ là phép & được viết khác.”** Không; **&&** short circuit, **&** trên boolean đánh giá cả hai vế.
7. **“Trong vòng for, continue bỏ luôn i++.”** Không; continue nhảy tới bước update rồi condition, khi đây là vòng for tương ứng.
8. **“if (a >= b) luôn tạo if_icmpge.”** Không; compiler có thể dùng nhánh phủ định **if_icmplt** để nhảy qua body.
9. **“Một câu lệnh gán compound được giải nghĩa bằng cách chép nguyên văn bên trái hai lần.”** Sai với receiver/index có side effects; Java bảo đảm vế trái được đánh giá một lần.
10. **“Một dòng source = một opcode.”** Không; compiler có thể tạo nhiều instruction, gộp hằng hoặc phát mã cho helper/exception table.
11. **“Giá trị trên operand stack là chính biến trong locals.”** Không; stack có thể giữ giá trị cũ trong khi local đã được cập nhật.
12. **“Output của một chương trình đa luồng có thể suy hết từ javap của một thread.”** Không; thứ tự giữa threads cần phân tích đồng bộ và memory model.

## 20. Bài tập dự đoán có đáp án

**Bài 1 — Prefix và postfix**

~~~java
int x = 2;
int y = x++ + ++x;
System.out.println("x=" + x + ",y=" + y);
~~~

**Đáp án:** **x=4,y=6**. Vế trái trả 2 rồi x=3; vế phải tăng x=4 rồi trả 4; tổng 6.

**Bài 2 — Thứ tự đánh giá**

~~~java
int n = 1;
int result = (n = 5) * n;
System.out.println("n=" + n + ",result=" + result);
~~~

**Đáp án:** **n=5,result=25**. Phép gán bên trái hoàn tất trước khi vế phải đọc n.

**Bài 3 — Gán compound trên mảng**

~~~java
int[] a = {4, 9};
int k = 0;
a[k++] += 2;
System.out.println("a0=" + a[0] + ",k=" + k);
~~~

**Đáp án:** **a0=6,k=1**. Index được lấy ở giá trị cũ 0; RHS cộng vào phần tử đó.

**Bài 4 — Continue**

~~~java
int sum = 0;
for (int i = 0; i < 4; i++) {
    if (i == 2) continue;
    sum += i;
}
System.out.println(sum);
~~~

**Đáp án:** **4** (0+1+3). Khi i=2, continue vẫn đi qua for-update để tăng i rồi kiểm tra lại.

**Bài 5 — Do-while**

~~~java
int a = 0;
do {
    a++;
} while (a < 0);
System.out.println(a);
~~~

**Đáp án:** **1**, bởi body chạy một lần trước condition.

**Bài 6 — Đọc nhánh phủ định**

Bytecode minh họa:

~~~text
iload_0
iconst_5
if_icmplt 12
iconst_1
ireturn
12: iconst_0
ireturn
~~~

**Đáp án:** Method trả **1** khi tham số **>= 5**, trả **0** khi tham số **< 5**. Opcode **if_icmplt** nhảy sang nhánh trả 0, nên nó là điều kiện phủ định nhánh trả 1.

**Bài 7 — Đánh giá ngắn mạch**

~~~java
int n = 0;
boolean ok = (n != 0) && (10 / n > 1);
System.out.println(ok);
~~~

**Đáp án:** In **false**, không ném **ArithmeticException**, vì vế phải của **&&** không được đánh giá khi n=0. Nếu đổi **&&** thành **&**, phép chia sẽ được đánh giá và lỗi xảy ra.

**Bài 8 — Operand stack**

Khi stack từ đáy tới đỉnh là **[8, 2]**, chạy **idiv** cho giá trị nào?

**Đáp án:** **4**, vì int thứ nhất/giá trị nằm dưới đỉnh là dividend 8, đỉnh stack là divisor 2. Không phải 2/8.

**Bài 9 — Vòng for trong javap**

Bạn thấy chuỗi instruction gồm **if_icmpge 25**, sau đó body, **iinc**, **goto 4**, rồi **25: ireturn**. Khi điều kiện so sánh **i >= limit** đúng thì chuyện gì xảy ra?

**Đáp án:** PC nhảy thẳng đến **offset 25** để trả về; nếu sai thì chạy body, iinc và goto quay về offset 4.

**Bài 10 — Giới hạn phân tích**

Bạn thấy **javap -c** của method có hai lệnh gọi **A()**, **B()** theo thứ tự lưu, nhưng method là callback bất đồng bộ và A/B có thể tạo thread. Có đủ cơ sở nói mọi log từ B luôn sau mọi log từ A không?

**Đáp án:** Không. Thứ tự đánh giá lời gọi trong thread ban đầu có quy tắc, còn hoạt động bên trong các thread khác có thể xen kẽ. Phải phân tích đồng bộ/happens-before riêng.

## 21. Tài liệu đặc tả

- **Java Language Specification SE 17, §15.7 — Evaluation Order**: https://docs.oracle.com/javase/specs/jls/se17/html/jls-15.html#jls-15.7
- **JLS §15.14–§15.15 — Postfix, Prefix Expressions**: https://docs.oracle.com/javase/specs/jls/se17/html/jls-15.html#jls-15.14
- **JLS §15.23–§15.24 — Conditional-And / Conditional-Or Operators**: https://docs.oracle.com/javase/specs/jls/se17/html/jls-15.html#jls-15.23
- **JLS §15.26 — Assignment Operators**: https://docs.oracle.com/javase/specs/jls/se17/html/jls-15.html#jls-15.26
- **JLS §14 — Blocks, Statements, and Patterns (for, while, break, continue)**: https://docs.oracle.com/javase/specs/jls/se17/html/jls-14.html
- **JVMS SE 17, §2.6 — Frames; §2.11 — Instruction Set Summary**: https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-2.html
- **JVMS SE 17, §6.5 — Instructions (iload, istore, iinc, branch, invoke, dup, array/field)**: https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-6.html

**Ghi nhớ:** muốn dự đoán thứ tự thực thi, đọc bytecode như **một luồng trạng thái** gồm **PC + locals + operand stack + side effects + nhánh**, chứ không đọc như danh sách lệnh chạy tuần tự đúng một lần. Java bảo đảm evaluation order quan sát được; javac hiện thực bằng các lệnh load/store, phép toán, nhảy và cấu trúc stack. Khi gặp **++**, **+=**, **&&**, loop và array assignment, luôn theo dõi **giá trị cũ, giá trị mới, số lần đánh giá và đường nhảy**. Khi thiếu một trong các yếu tố này, rất dễ suy ra sai output dù bạn nhận diện đúng từng opcode.


</details>

- [Quay lại đầu trang](#back-to-top)
