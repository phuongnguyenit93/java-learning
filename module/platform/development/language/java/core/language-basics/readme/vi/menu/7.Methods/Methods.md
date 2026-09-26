# Method

Method đặt tên cho một hành vi có thể tái sử dụng. Nhưng một lời gọi Java không chỉ là “tìm method cùng tên”: compiler còn phải xét signature, chuyển đổi và overload resolution.

## <a id="method-signature">Method Signature</a>

Ở mức overload trong Java, method signature chủ yếu gồm **tên method + parameter types**. Return type không đủ để tạo overload khác nhau.

```java
int parse(String value) { return Integer.parseInt(value); }
long parse(String value) { return Long.parseLong(value); } // vẫn không hợp lệ: chỉ khác return type
```

Parameter là local variable nhận đối số value khi method được gọi. `return` kết thúc method và cung cấp result nếu return type không phải `void`.

### Anatomy của một method

```java
public int add(int left, int right) {
    return left + right;
}
```

Có thể đọc theo thứ tự:

```text
modifier → return type → method name → parameter list → body
```

Method parameter khai báo **input contract ở mức type**. Return type khai báo loại value mà caller nhận lại. `void` nghĩa method không trả một result value, không có nghĩa method không thể tạo side effect.

### Non-void method không được complete normally mà thiếu value

Nếu method khai báo return type khác `void`, compiler phải chứng minh method body **không thể kết thúc bình thường mà không trả value**:

```java
int find(boolean found) {
    if (found) {
        return 1;
    }
    // compile error: path này có thể đi tới cuối method mà không return int
}
```

Sửa bằng cách bảo đảm mọi normal path đều trả value:

```java
int find(boolean found) {
    if (found) {
        return 1;
    }
    return 0;
}
```

Một path kết thúc bằng `throw` không cần `return` tiếp theo vì execution không complete normally trên path đó:

```java
int requireValue(boolean valid) {
    if (!valid) {
        throw new IllegalStateException("invalid");
    }
    return 1;
}
```

Đây là cùng kiểu **compile-time control-flow analysis** đã gặp ở definite assignment: compiler phân tích path, không đoán runtime intent.

Java không overload chỉ bằng parameter name hoặc return type:

```java
void save(String name) { }
// void save(String value) { } // cùng signature
```

## <a id="method-invocation-conversion">Chuyển đổi khi gọi Method</a>

Để một method candidate áp dụng được, đối số có thể trải qua các chuyển đổi mà Java cho phép trong method invocation ngữ cảnh, như:

- identity chuyển đổi;
- primitive widening;
- reference widening;
- boxing/unboxing trong phase phù hợp;
- varargs chuyển đổi ở phase cuối.

Không phải mọi cast hợp lệ đều được compiler tự thực hiện trong method call.

Ví dụ:

```java
void use(long value) { }
use(10); // int widening thành long

void boxed(Integer value) { }
boxed(10); // boxing
```

Nhưng compiler không tự narrowing tùy ý:

```java
void useByte(byte value) { }
int x = 1;
// useByte(x); // không compile
useByte((byte) x);
```

Điều này quan trọng vì overload resolution dùng **các conversion được language cho phép**, không dùng "conversion mà runtime value tình cờ an toàn".

## <a id="overload-resolution-phases">Các bước Overload Resolution</a>

Compiler xét overload theo các phase ưu tiên. mô hình tư duy hữu ích:

```text
1. fixed arity, không cần boxing/varargs mở rộng
        ↓ nếu chưa có candidate phù hợp
2. cho phép boxing/unboxing phù hợp
        ↓ nếu vẫn chưa có
3. varargs fallback
```

Chi tiết specification sâu hơn, nhưng order này giải thích nhiều câu hỏi phỏng vấn kiểu “widening, boxing hay varargs thắng?”.

Ví dụ kinh điển:

```java
void pick(long x) { System.out.println("long"); }
void pick(Integer x) { System.out.println("Integer"); }
void pick(int... x) { System.out.println("varargs"); }

pick(1); // long
```

`int -> long` được giải quyết ở phase fixed-arity không boxing, nên thắng trước boxing và varargs.

## <a id="most-specific-overload">Most-specific Overload</a>

Nếu nhiều candidate cùng áp dụng, compiler cố chọn candidate **cụ thể hơn** theo type các quy tắc.

```java
void print(Object x) { }
void print(String x) { }

print("java"); // String overload
```

Không phải “method khai báo sau” hay “method có body tốt hơn” thắng; đây là quyết định compile-time dựa trên type.

Nếu compiler không thể tìm một candidate cụ thể hơn hợp lệ, call sẽ ambiguous thay vì "chọn đại":

```java
void use(CharSequence x) { }
void use(Serializable x) { }

String value = "x";
// use(value); // có thể ambiguous vì String phù hợp cả hai và không overload nào cụ thể hơn overload kia
```

## <a id="null-overload-ambiguity">null và Overload Ambiguity</a>

Literal `null` tương thích với reference type. Nếu các overload không có quan hệ specificity rõ ràng:

```java
void print(String x) { }
void print(Integer x) { }

print(null); // ambiguous
```

compiler không thể chọn một overload cụ thể.

Cast explicit có thể disambiguate nếu đó thực sự là intent.

Nếu các overload có quan hệ subtype rõ, compiler vẫn có thể chọn most-specific:

```java
void print(Object x) { }
void print(String x) { }

print(null); // chọn String vì String cụ thể hơn Object
```

Vì vậy `null` không tự động gây ambiguity; ambiguity xuất hiện khi không có một candidate thắng rõ theo specificity rules.

## <a id="method-call-evaluation">Thứ tự Evaluation của Argument</a>

Java evaluate đối số biểu thức từ trái sang phải trước khi method body chạy.

Side effect trong đối số vẫn có thể làm mã khó hiểu:

```java
call(i++, update(i));
```

Khi thứ tự có ý nghĩa business, tách calculation ra biến riêng thường rõ hơn.

Receiver và argument expression được evaluate trước khi method body chạy. Method chỉ nhận **kết quả value** của các expression đó; nó không nhận chính expression hoặc variable slot của caller.

Điều này nối trực tiếp sang chapter Pass-by-Value.

## <a id="recursion-stack">Recursion và Call Stack</a>

Recursive method gọi lại chính nó hoặc một cycle method khác. Mỗi lời gọi cần một stack frame mới.

Nếu không có base case hoặc depth quá lớn, chương trình có thể gặp `StackOverflowError`.

Recursion phù hợp tự nhiên với một số tree/divide-and-conquer problem, nhưng loop có thể đơn giản và an toàn stack hơn cho iteration tuyến tính dài.

Ví dụ:

```java
int factorial(int n) {
    if (n <= 1) {
        return 1;          // base case
    }
    return n * factorial(n - 1); // recursive step
}
```

Mental model:

```text
factorial(3)
→ frame n=3 chờ factorial(2)
→ frame n=2 chờ factorial(1)
→ frame n=1 trả 1
→ unwind: 2 → 6
```

Mỗi call giữ state riêng. Java không guarantee tail-call optimization, nên recursion depth vẫn là một constraint runtime thực tế.

chương tiếp theo xem cú pháp đặc biệt cho method nhận số đối số thay đổi: varargs.
