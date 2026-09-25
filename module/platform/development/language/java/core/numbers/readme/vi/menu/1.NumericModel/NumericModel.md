# Mô hình số trong Java

## <a id="numeric-type-model">Mô hình numeric type của Java</a>
Java tách primitive numeric type có độ rộng cố định khỏi arbitrary-precision object type. `byte`, `short`, `int`, `long` là signed two's-complement integer; `float`, `double` theo IEEE 754 binary floating-point. `BigInteger` và `BigDecimal` là immutable object cho value vượt primitive range hoặc cần decimal arithmetic.

## <a id="integer-vs-floating">Integer và floating-point semantics</a>
Integer arithmetic exact trong phạm vi biểu diễn và wrap khi overflow nếu không dùng exact-checking method. Floating-point có dynamic range lớn nhưng phần lớn decimal fraction chỉ được biểu diễn gần đúng và có special value như NaN/infinity.

## <a id="numeric-conversions">Numeric conversion và promotion</a>
Arithmetic áp dụng promotion/conversion rule trước khi operation chạy. Integer nhỏ thường được nâng lên `int`; mixed expression thường tiến về type rộng hơn. Cast có thể narrow kết quả nhưng không phục hồi information đã mất trong operation trước đó.
