# Quy tắc bằng nhau và thứ tự của giá trị số

`BigDecimal` có hai câu hỏi so sánh khác nhau:

```text
cách biểu diễn có giống nhau không?
và
giá trị số có bằng nhau không?
```

Nếu không phân biệt hai câu hỏi này, lỗi thường xuất hiện khi dùng BigDecimal trong collection hoặc làm khóa của bài toán.

## <a id="big-decimal-equals">equals và Scale</a>

`BigDecimal.equals` xét cả giá trị số **và scale**:

```java
BigDecimal a = new BigDecimal("1.0");
BigDecimal b = new BigDecimal("1.00");

System.out.println(a.equals(b)); // false
```

Ta có:

```text
a → unscaled 10,  scale 1
b → unscaled 100, scale 2
```

Hai đối tượng biểu diễn cùng một đại lượng toán học nhưng cách biểu diễn khác nhau.

### VÌ SAO equals làm như vậy?

`BigDecimal` xem scale là một phần của cách biểu diễn giá trị. Vì vậy `equals/hashCode` giữ sự khác biệt này.

Điều đó có nghĩa mã băm của `1.0` không cần bằng mã băm của `1.00`.

## <a id="big-decimal-compareto">compareTo và giá trị số</a>

`compareTo` trả lời câu hỏi về thứ tự/giá trị số:

```java
BigDecimal a = new BigDecimal("1.0");
BigDecimal b = new BigDecimal("1.00");

System.out.println(a.compareTo(b)); // 0
```

Cách ghi nhớ:

```text
equals
→ cách biểu diễn giá trị + scale

compareTo
→ thứ tự theo giá trị số
```

Đây là một ngoại lệ nổi tiếng đối với khuyến nghị chung rằng `compareTo == 0` nên nhất quán với `equals`.

### So sánh với số 0

Nếu mục đích là kiểm tra giá trị số có bằng 0 hay không, cách sau thường rõ hơn:

```java
value.compareTo(BigDecimal.ZERO) == 0
```

vì `0.00` không `equals(BigDecimal.ZERO)` nhưng về mặt số học vẫn bằng 0.

## <a id="big-decimal-collections">Hệ quả khi dùng trong Collection</a>

### HashSet

```java
Set<BigDecimal> values = new HashSet<>();
values.add(new BigDecimal("1.0"));
values.add(new BigDecimal("1.00"));

System.out.println(values.size()); // 2
```

`HashSet` dùng `equals/hashCode`.

### TreeSet

```java
Set<BigDecimal> values = new TreeSet<>();
values.add(new BigDecimal("1.0"));
values.add(new BigDecimal("1.00"));

System.out.println(values.size()); // 1
```

`TreeSet` mặc định dùng thứ tự tự nhiên qua `compareTo`.

Đây là một trường hợp hiếm khi hai loại collection có thể coi cùng một cặp giá trị là “trùng lặp” theo hai cách khác nhau.

### Chuẩn hóa không phải giải pháp thần kỳ

`stripTrailingZeros()` có thể giúp đưa một số giá trị về dạng chuẩn:

```java
BigDecimal normalized =
        new BigDecimal("1.00").stripTrailingZeros();
```

nhưng chính sách chuẩn hóa phải được chọn có chủ ý. Nó có thể thay đổi scale, kể cả thành scale âm ở một số giá trị.

Nếu BigDecimal được dùng làm khóa của bài toán, hãy quyết định rõ:

```text
định danh dựa trên cách biểu diễn?
hay
định danh dựa trên đại lượng số học?
```

Sau các cách biểu diễn phức tạp, chương tiếp theo quay lại các tiện ích số chuẩn trong `Math`.
