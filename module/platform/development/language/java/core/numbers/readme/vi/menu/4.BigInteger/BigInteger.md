# BigInteger

Khi `long` không còn đủ phạm vi, lựa chọn đúng thường không phải “hy vọng overflow không xảy ra” mà là dùng một cách biểu diễn không bị fixed-width primitive giới hạn.

## <a id="big-integer-model">BigInteger là gì?</a>

`BigInteger` biểu diễn integer với độ chính xác tùy ý trong giới hạn bộ nhớ thực tế.

```java
BigInteger value = new BigInteger("123456789012345678901234567890");
```

Nó phù hợp cho các bài toán như large counters, cryptographic arithmetic hoặc combinatorial values vượt xa `long`.

Đánh đổi là object allocation và arithmetic cost cao hơn primitive integer.

## <a id="big-integer-immutability">BigInteger là Immutable</a>

thao tác không sửa object hiện tại mà trả object mới:

```java
BigInteger a = BigInteger.TEN;
a.add(BigInteger.ONE); // a vẫn là 10
a = a.add(BigInteger.ONE);
```

mô hình tư duy giống `String`: variable có thể được gán lại, nhưng từng `BigInteger` object không thay đổi value sau khi tạo.

Immutability giúp sharing an toàn hơn và làm value ngữ nghĩa dễ suy luận.

## <a id="big-integer-operations">Operation và Conversion Boundary</a>

`BigInteger` cung cấp các method như:

- `add`, `subtract`, `multiply`, `divide`;
- `pow`, `gcd`, `mod`;
- bit các thao tác;
- chuyển đổi về primitive.

chuyển đổi về `int`/`long` là một ranh giới quan trọng. Method như `intValue()` có thể truncate, còn `intValueExact()`/`longValueExact()` giúp phát hiện giá trị không fit primitive phạm vi.

Nếu vấn đề không phải integer phạm vi mà là **decimal value chính xác**, `BigInteger` chưa giải quyết được; chương tiếp theo là `BigDecimal`.
