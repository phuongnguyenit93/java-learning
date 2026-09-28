# Các mô hình hiện thực AOP

## <a id="aop-compile-time">1. Compile-time weaving</a>

Aspect được kết hợp với target trong quá trình build/compile. Kết quả tạo ra đã chứa behavior bổ sung trước khi ứng dụng chạy.

## <a id="aop-load-time">2. Load-time weaving</a>

Class được điều chỉnh khi được nạp vào runtime. Cơ chế này cho phép composition xảy ra muộn hơn compile time nhưng trước khi execution bình thường bắt đầu.

## <a id="aop-runtime">3. Runtime interception</a>

Một số implementation áp dụng cross-cutting behavior tại runtime thông qua một lớp trung gian hoặc interception mechanism.

Mental model:

```text
caller
  ↓
interception boundary
  ↓
cross-cutting behavior
  ↓
target
```

## <a id="aop-model-boundary">4. Vì sao phải phân biệt các model?</a>

Các implementation model có capability và limitation khác nhau.

Một runtime interception model có thể chỉ quan sát một số invocation boundary, trong khi weaving model có thể tác động sâu hơn vào execution structure.

Vì vậy khi học một framework AOP cụ thể, cần tách rõ **AOP concept** và **cơ chế implementation của framework đó**.
