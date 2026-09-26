# Primitive và Reference

Trước khi học `if`, method, array hay OOP, cần trả lời một câu rất cơ bản: **một biến Java thực sự đang giữ loại giá trị nào?**

Java có hai nhóm lớn trong mô hình tư duy này:

```text
giá trị primitive
→ bản thân giá trị số, logic hoặc ký tự primitive

giá trị tham chiếu (reference)
→ dùng để tham chiếu tới object/array, hoặc mang null
```

Sự khác biệt này nối xuyên suốt Java Core: phép gán, boxing, casting, lời gọi method, pass-by-value, array, `null`, equality và runtime type.

Lộ trình của module:

```text
Biến Java giữ loại giá trị nào?
Primitive vs Reference
        ↓
Tên biến tồn tại và nhìn thấy ở đâu?
Biến và Scope
        ↓
Primitive tham gia object/generic API thế nào?
Wrapper & Boxing
        ↓
Giá trị được kết hợp và chuyển kiểu thế nào?
Operators → Casting
        ↓
Chương trình chọn nhánh chạy ra sao?
Control Flow
        ↓
Hành vi được đặt tên và gọi thế nào?
Methods → Varargs → Pass-by-Value
        ↓
Dãy có kích thước cố định được biểu diễn ra sao?
Arrays
        ↓
Tên type được tổ chức thế nào?
Packages & Imports
        ↓
Tham chiếu không trỏ tới object nào nghĩa là gì?
null
        ↓
Compile-time type và runtime type nối với nhau thế nào?
Type System Mental Model
```

## <a id="primitive-vs-reference-model">Primitive và Reference</a>

### KHÁI NIỆM

Biến primitive giữ trực tiếp một giá trị primitive:

```java
int age = 20;
boolean active = true;
```

Biến reference giữ một **giá trị tham chiếu** có thể nhận diện một object/array hoặc mang `null`:

```java
User user = new User();
int[] values = new int[3];
```

Điểm quan trọng: Java vẫn sao chép **giá trị** khi gán hoặc truyền đối số. Với biến reference, giá trị được sao chép chính là giá trị tham chiếu.

```java
List<String> a = new ArrayList<>();
List<String> b = a; // sao chép giá trị tham chiếu
```

`a` và `b` giờ cùng tham chiếu một list object.

## <a id="primitive-ranges-and-defaults">Phạm vi và giá trị mặc định</a>

Java có tám primitive type:

```text
byte, short, int, long
float, double
char
boolean
```

Primitive số nguyên có phạm vi cố định; floating-point tuân theo IEEE 754; `char` là một UTF-16 code unit 16-bit; `boolean` biểu diễn logic `true/false`.

Field nhận giá trị mặc định khi trạng thái của object hoặc class được khởi tạo, ví dụ số là `0`, boolean là `false`, reference là `null`. Biến cục bộ **không tự có giá trị mặc định để đọc ngay**; compiler yêu cầu definite assignment trước khi sử dụng.

Numeric literal cũng có kiểu ở compile time. Suffix như `L`, `F`, tiền tố radix hoặc constant expression có thể quyết định phép gán có hợp lệ hay không.

## <a id="reference-value-semantics">Giá trị tham chiếu</a>

Java không yêu cầu lập trình viên coi reference như một địa chỉ bộ nhớ vật lý có thể thao tác trực tiếp.

Mô hình tư duy đủ dùng:

```text
giá trị tham chiếu
→ có thể nhận diện object/array
→ có thể được sao chép
→ có thể so identity bằng ==
→ có thể được dereference
→ có thể là null
```

Một object có thể có nhiều alias. Mất một reference không đồng nghĩa object bị hủy nếu vẫn còn đường tham chiếu khác tới object đó.

chương tiếp theo hỏi: **một biến thuộc loại nào, tồn tại trong bao lâu, và tên của nó nhìn thấy ở đâu?**
