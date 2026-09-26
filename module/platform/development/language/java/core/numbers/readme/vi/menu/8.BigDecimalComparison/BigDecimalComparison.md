# So sánh BigDecimal

`BigDecimal` có hai câu hỏi comparison khác nhau:

```text
representation có giống nhau không?
và
numerical value có bằng nhau không?
```

Nếu không phân biệt hai câu hỏi này, bug thường xuất hiện khi dùng BigDecimal trong collection hoặc làm domain key.

## <a id="big-decimal-equals">equals và Scale</a>

`BigDecimal.equals` xét cả numerical value **và scale**:

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

Hai object biểu diễn cùng quantity toán học nhưng representation khác.

### WHY equals làm như vậy?

`BigDecimal` xem scale là một phần của value representation. Vì vậy `equals/hashCode` giữ distinction này.

Điều đó có nghĩa hash code của `1.0` không cần bằng hash code của `1.00`.

## <a id="big-decimal-compareto">compareTo và Numerical Value</a>

`compareTo` trả lời câu hỏi ordering/numerical value:

```java
BigDecimal a = new BigDecimal("1.0");
BigDecimal b = new BigDecimal("1.00");

System.out.println(a.compareTo(b)); // 0
```

Mental model:

```text
equals
→ value representation + scale

compareTo
→ numerical ordering
```

Đây là một exception nổi tiếng đối với khuyến nghị chung rằng `compareTo == 0` nên nhất quán với `equals`.

### Compare với zero

Nếu intent là numerical zero, thường rõ hơn:

```java
value.compareTo(BigDecimal.ZERO) == 0
```

vì `0.00` không `equals(BigDecimal.ZERO)` nhưng numerically bằng zero.

## <a id="big-decimal-collections">Hệ quả với Collection</a>

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

`TreeSet` mặc định dùng natural ordering qua `compareTo`.

Đây là một case hiếm nơi hai collection type có thể coi cùng pair value là “duplicate” theo hai cách khác nhau.

### Normalization không phải magic fix

`stripTrailingZeros()` có thể giúp canonicalize một số value:

```java
BigDecimal normalized =
        new BigDecimal("1.00").stripTrailingZeros();
```

nhưng normalization policy phải được chọn có chủ ý. Nó có thể thay scale, kể cả thành scale âm ở một số value.

Nếu BigDecimal là domain key, hãy quyết định rõ:

```text
identity dựa trên representation?
hay
identity dựa trên numerical quantity?
```

Sau các representation phức tạp, chương tiếp theo quay lại standard numeric helpers trong `Math`.
