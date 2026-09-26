# Method

Method đặt tên cho một hành vi có thể tái sử dụng. Nhưng một lời gọi Java không chỉ là “tìm method cùng tên”: compiler còn phải xét signature, chuyển đổi và overload resolution.

## <a id="method-signature">Method Signature</a>

Ở mức overload trong Java, method signature chủ yếu gồm **tên method + parameter types**. Return type không đủ để tạo overload khác nhau.

```java
int parse(String value) { ... }
long parse(String value) { ... } // không hợp lệ chỉ vì return type khác
```

Parameter là local variable nhận đối số value khi method được gọi. `return` kết thúc method và cung cấp result nếu return type không phải `void`.

## <a id="method-invocation-conversion">Chuyển đổi khi gọi Method</a>

Để một method candidate áp dụng được, đối số có thể trải qua các chuyển đổi mà Java cho phép trong method invocation ngữ cảnh, như:

- identity chuyển đổi;
- primitive widening;
- reference widening;
- boxing/unboxing trong phase phù hợp;
- varargs chuyển đổi ở phase cuối.

Không phải mọi cast hợp lệ đều được compiler tự thực hiện trong method call.

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

## <a id="most-specific-overload">Most-specific Overload</a>

Nếu nhiều candidate cùng áp dụng, compiler cố chọn candidate **cụ thể hơn** theo type các quy tắc.

```java
void print(Object x) { }
void print(String x) { }

print("java"); // String overload
```

Không phải “method khai báo sau” hay “method có body tốt hơn” thắng; đây là quyết định compile-time dựa trên type.

## <a id="null-overload-ambiguity">null và Overload Ambiguity</a>

Literal `null` tương thích với reference type. Nếu các overload không có quan hệ specificity rõ ràng:

```java
void print(String x) { }
void print(Integer x) { }

print(null); // ambiguous
```

compiler không thể chọn một overload cụ thể.

Cast explicit có thể disambiguate nếu đó thực sự là intent.

## <a id="method-call-evaluation">Thứ tự Evaluation của Argument</a>

Java evaluate đối số biểu thức từ trái sang phải trước khi method body chạy.

Side effect trong đối số vẫn có thể làm mã khó hiểu:

```java
call(i++, update(i));
```

Khi thứ tự có ý nghĩa business, tách calculation ra biến riêng thường rõ hơn.

## <a id="recursion-stack">Recursion và Call Stack</a>

Recursive method gọi lại chính nó hoặc một cycle method khác. Mỗi lời gọi cần một stack frame mới.

Nếu không có base case hoặc depth quá lớn, chương trình có thể gặp `StackOverflowError`.

Recursion phù hợp tự nhiên với một số tree/divide-and-conquer problem, nhưng loop có thể đơn giản và an toàn stack hơn cho iteration tuyến tính dài.

chương tiếp theo xem cú pháp đặc biệt cho method nhận số đối số thay đổi: varargs.
