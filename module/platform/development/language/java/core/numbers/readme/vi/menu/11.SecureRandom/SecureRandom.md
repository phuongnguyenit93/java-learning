# SecureRandom và ranh giới bảo mật

## <a id="secure-random-purpose">Mục đích của SecureRandom</a>
`SecureRandom` là JDK abstraction cho cryptographically strong random byte/number. Nó dùng cho security-sensitive value khi attacker không được dự đoán khả thi output tương lai từ các output đã quan sát.

## <a id="entropy-seeding">Mental model về entropy và seeding</a>
Secure generator cần entropy phù hợp để initialize/reseed internal state. Application thường nên để provider tự seed `SecureRandom` thay vì truyền timestamp, counter hay hard-coded seed có entropy thấp. Provider/OS có thể ảnh hưởng startup/blocking behavior.

## <a id="security-boundary">Boundary sang security-cryptography</a>
Module này chỉ chốt randomness boundary: dùng `SecureRandom` khi unpredictability là security property. Key generation, IV/nonce requirement, salt, algorithm, provider, signature và encryption thuộc `java/advance/security-cryptography`, nơi cryptographic contract phải được học đầy đủ.
