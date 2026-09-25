# toString

## <a id="tostring-purpose">Mục đích của toString</a>
`toString` cung cấp human-oriented diagnostic representation của object. `Object.toString` cho class/identity-like information; domain class thường override để hiển thị state hữu ích cho log, debug và test.

## <a id="tostring-design">Representation hữu ích và deterministic</a>
Representation tốt nên concise, đủ stable cho con người và làm field quan trọng dễ hiểu nhưng không giả vờ là serialization format. Tránh expensive computation, hidden I/O hoặc behavior có thể throw bất ngờ trong logging/debugging.

## <a id="tostring-sensitive-data">Boundary sensitive data và logging</a>
Không đưa password, token, secret, full payment data hay sensitive value vào chỉ vì chúng là field. `toString` thường được gọi implicit bởi logging, concatenation, IDE và error handling nên exposure surface rộng hơn call site explicit.
