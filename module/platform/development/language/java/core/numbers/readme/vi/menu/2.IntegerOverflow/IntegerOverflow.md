# Số nguyên và Overflow

## <a id="integer-overflow-wraparound">Integer overflow và wraparound</a>
Primitive integer operation không tự throw khi overflow. Kết quả wrap theo độ rộng type với two's-complement semantics.

```java
int x = Integer.MAX_VALUE;
int y = x + 1; // Integer.MIN_VALUE
```

Overflow là behavior xác định của language nhưng thường là bug khi value biểu diễn tiền, counter, size hay identifier.

## <a id="checked-arithmetic">Checked arithmetic bằng exact methods</a>
`Math.addExact`, `subtractExact`, `multiplyExact`, `incrementExact`, `decrementExact` và conversion helper tương tự sẽ throw `ArithmeticException` nếu result không represent được. Dùng chúng khi silent wraparound không chấp nhận được.

## <a id="boundary-values">Suy luận ở MIN/MAX boundary</a>
Mọi fixed-width integer type có `MIN_VALUE` và `MAX_VALUE`. Signed range không đối xứng; ví dụ `-Integer.MIN_VALUE` vẫn là `Integer.MIN_VALUE` vì positive counterpart không represent được. Boundary test nên gồm zero, sát limit và sign transition.
