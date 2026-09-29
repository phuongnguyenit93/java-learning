# Declarative Programming

## <a id="declarative-what">1. Declarative Programming là gì?</a>

Declarative Programming là paradigm nhấn mạnh việc mô tả **kết quả, constraint hoặc rule mong muốn**, thay vì liệt kê đầy đủ từng bước execution phải diễn ra như thế nào.

```text
WHAT is desired
→ runtime / engine / implementation decides HOW
```

## <a id="declarative-why">2. Tại sao nó tồn tại?</a>

Trong nhiều domain, người viết code quan tâm nhiều hơn đến intent so với execution detail. Việc tách hai phần này giúp expression ngắn hơn và cho implementation có quyền tối ưu cách thực thi.

## <a id="declarative-before">3. Nếu dùng imperative thì sao?</a>

Imperative code hoàn toàn có thể tạo cùng kết quả bằng cách mô tả từng bước.

Declarative style hữu ích khi execution detail lặp lại, phức tạp hoặc có thể được framework/engine xử lý tốt hơn.

## <a id="declarative-solution">4. Mental model</a>

```text
desired condition / result
        ↓
declaration
        ↓
engine chooses execution strategy
```

## <a id="declarative-relations">5. Quan hệ với các style khác</a>

Declarative là một umbrella concept. Functional Programming thường có tính declarative cao nhưng không đồng nghĩa hoàn toàn với Declarative Programming.

Logic programming, query languages và rule/configuration systems cũng thường mang tính declarative.

Module này chỉ sở hữu **mental model và relationship**; các DSL hoặc technology cụ thể vẫn thuộc canonical owner riêng.
