<a id="back-to-top"></a>

# Class File, Bytecode và Ranh giới Trước Thực thi

## Menu
- [Tổng quan class file format](#classfile-format-overview)
- [Cấu trúc ClassFile và các thành phần chính](#classfile-structure)
- [Mô hình bytecode instruction](#bytecode-instruction-model)
- [Constant pool và symbolic references](#constant-pool-symbolic-references)
- [Từ constant pool đến runtime constant pool](#runtime-constant-pool)
- [Loading, linking và initialization ở mức JVM](#loading-linking-initialization-overview)
- [Verification, preparation và resolution](#verification-preparation-resolution)
- [Failure trước khi method body được thực thi](#pre-execution-failures)

## <a id="classfile-format-overview">Tổng quan class file format</a>

<details>
<summary>Click for details</summary>

Class file là một biểu diễn nhị phân có cấu trúc chặt chẽ. Mỗi class file mô tả một class, interface hoặc module theo version của class-file format tương ứng.

Ở mức cao, nó chứa:

```text
magic + version
constant_pool
access_flags
this_class / super_class / interfaces
fields
methods
attributes
```

Magic value `0xCAFEBABE` cho phép runtime nhận diện format; major/minor version giúp JVM xác định artifact có dùng class-file version mà runtime hiện tại hỗ trợ hay không.

Đừng hiểu class file như “mã nguồn Java đã nén lại”. Nó là một biểu diễn riêng, có vocabulary và constraint riêng, được thiết kế cho JVM execution.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="classfile-structure">Cấu trúc ClassFile và các thành phần chính</a>

<details>
<summary>Click for details</summary>

`ClassFile` là một sequence có schema xác định. Các entry thường không chứa toàn bộ text lặp lại; nhiều thành phần tham chiếu qua index vào constant pool.

Ví dụ conceptual:

```text
method_info
  name_index ─────────┐
  descriptor_index ─┐│
                    ↓↓
              constant_pool
```

Method không phải abstract/native thường có `Code` attribute. `Code` chứa instruction bytes, max stack, local-variable capacity, exception table và nested attributes như line-number/debug metadata.

Từ góc nhìn người học, cấu trúc này giải thích vì sao tool như `javap -v` có thể hiển thị bytecode, constant pool, descriptor và attribute một cách có hệ thống.

Một cách quan sát trực tiếp:

```text
javac Demo.java
javap -c -v Demo
```

`javac` tạo class file; `javap` cho phép nhìn lại chính biểu diễn mà JVM sẽ đọc thay vì chỉ nhìn source.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="bytecode-instruction-model">Mô hình bytecode instruction</a>

<details>
<summary>Click for details</summary>

Bytecode là instruction set của JVM. Mỗi instruction có opcode và có thể có operand đi kèm. Instruction thường thao tác trên operand stack, local variables, object/array hoặc symbolic reference trong runtime constant pool.

Ví dụ source:

```java
int add(int a, int b) {
    return a + b;
}
```

Conceptually có thể trở thành:

```text
iload_1
iload_2
iadd
ireturn
```

Điểm đáng học không phải nhớ opcode, mà là hiểu execution model **stack-oriented**: value được load lên operand stack, instruction consume/produce value, rồi kết quả có thể được return hoặc store.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="constant-pool-symbolic-references">Constant pool và symbolic references</a>

<details>
<summary>Click for details</summary>

Constant pool là bảng dữ liệu trung tâm của class file. Ngoài literal constant, nó chứa symbolic information về class, field, method, name-and-type, method handle, invokedynamic entry và nhiều loại khác.

JVM instruction không cần nhúng trực tiếp memory address của method/field. Thay vào đó, instruction có thể tham chiếu một entry trong constant pool:

```text
invokevirtual #12
              ↓
CONSTANT_Methodref
              ↓
class + name + descriptor
```

Nhờ symbolic reference, class file không cần biết object layout hoặc địa chỉ machine code tại compile time. Resolution ở runtime sẽ nối symbolic identity với runtime entity phù hợp.

Đây là cầu nối quan trọng giữa biểu diễn class file và runtime linking.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="runtime-constant-pool">Từ constant pool đến runtime constant pool</a>

<details>
<summary>Click for details</summary>

Khi JVM tạo class/interface ở runtime, thông tin từ class-file constant pool được dùng để tạo **runtime constant pool** tương ứng. Runtime constant pool đóng vai trò gần giống symbol table trong compiler/runtime truyền thống.

Nó chứa:

- static constants đã sẵn sàng sử dụng;
- symbolic references có thể cần resolution;
- biểu diễn runtime phục vụ instruction execution.

Điểm quan trọng: runtime constant pool là **runtime data structure**, không phải chỉ là bytes còn nằm nguyên trong file.

Resolution có thể xảy ra ở thời điểm implementation lựa chọn trong giới hạn của specification. Vì vậy không nên giả định “mọi symbolic reference được resolve ngay khi class vừa load”.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="loading-linking-initialization-overview">Loading, linking và initialization ở mức JVM</a>

<details>
<summary>Click for details</summary>

Trước khi một class/interface có thể tham gia execution đầy đủ, JVM có ba khái niệm lifecycle lớn:

```text
Loading
→ tìm/nhận biểu diễn nhị phân và tạo class/interface runtime

Linking
→ verification + preparation + có thể resolution

Initialization
→ thực thi class/interface initialization method <clinit>
```

Loading trả lời “runtime đã có biểu diễn của type này chưa?”. Linking trả lời “biểu diễn đó đã đủ hợp lệ và được tích hợp vào runtime state chưa?”. Initialization trả lời “static initialization semantics đã được thực thi chưa?”.

Module JVM chỉ cần nắm flow và ranh giới lỗi. Chi tiết ClassLoader API, delegation và class-loader identity thuộc module ClassLoader.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="verification-preparation-resolution">Verification, preparation và resolution</a>

<details>
<summary>Click for details</summary>

**Verification** kiểm tra biểu diễn class có đáp ứng structural/type-safety constraints cần thiết để JVM có thể thực thi an toàn hay không.

**Preparation** tạo static field storage và đặt default value theo JVM rules trước khi Java-level initializer chạy.

**Resolution** chuyển symbolic reference thành runtime reference trực tiếp hơn tới class/interface/field/method tương ứng.

Ví dụ:

```java
static int port = 8080;
```

Conceptually:

```text
preparation   → storage tồn tại, value ban đầu = 0
initialization→ chạy initializer, value = 8080
```

Với `static final` constant variable có `ConstantValue` attribute, constant value cũng được gán trong **initialization procedure**, trước khi JVM gọi `<clinit>`; nó vẫn không phải là bước preparation. Vì vậy preparation nên nhớ đơn giản là “tạo static storage + default value”, còn các giá trị Java-level thực sự được thiết lập trong initialization.

Tách preparation khỏi initialization giúp hiểu nhiều câu hỏi về static state mà source-level mental model thường che khuất.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="pre-execution-failures">Failure trước khi method body được thực thi</a>

<details>
<summary>Click for details</summary>

Không phải mọi runtime failure đều xảy ra “trong method body”. Class-file format, verification, linking hoặc initialization có thể fail trước khi bytecode mục tiêu được thực thi.

Các nhóm lỗi người học nên phân biệt:

- biểu diễn class/version không hợp lệ hoặc không được hỗ trợ;
- verification failure;
- missing/incompatible class/member trong linkage;
- lỗi trong static initialization.

Ví dụ thực tế: ứng dụng có thể compile với một version của dependency nhưng runtime lại load version khác. Method call chưa kịp chạy đã có thể fail trong linkage vì symbolic reference không còn khớp với runtime type.

Mental model đúng là:

```text
source compiled thành công
≠ bảo đảm runtime linkage thành công
```

Chi tiết tìm kiếm class nào được load từ đâu thuộc ClassLoader/Diagnostics; ở đây ta chỉ xác định failure nằm trước execution engine.

</details>

- [Quay lại đầu trang](#back-to-top)
