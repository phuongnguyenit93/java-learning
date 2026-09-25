# BigInteger

## <a id="big-integer-model">Mô hình arbitrary-precision integer</a>
`BigInteger` biểu diễn signed integer với magnitude chủ yếu bị giới hạn bởi memory thay vì primitive width cố định. Nó phù hợp cho math/crypto domain, counter rất lớn, combinatorics và exact integer calculation vượt `long`.

## <a id="big-integer-immutability">BigInteger là immutable</a>
Mọi arithmetic method trả `BigInteger` mới; receiver không bị mutate.

```java
BigInteger a = new BigInteger("1000");
a.add(BigInteger.ONE);     // bỏ mất result
BigInteger b = a.add(BigInteger.ONE); // 1001
```

## <a id="big-integer-operations">Core operation và conversion boundary</a>
`BigInteger` hỗ trợ arithmetic, division/remainder, power, gcd, bit operation và primality helper. Convert về primitive có thể truncate với `intValue`; dùng `intValueExact`/`longValueExact` khi out-of-range phải fail thay vì silently mất bit.
