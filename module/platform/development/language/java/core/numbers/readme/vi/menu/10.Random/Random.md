# Sinh số ngẫu nhiên

## <a id="pseudo-random-model">Pseudo-random model và seed</a>
Pseudo-random generator tạo sequence deterministic từ internal state. Cùng algorithm/seed có thể reproduce cùng sequence, hữu ích cho test/simulation. Output nhìn random không đồng nghĩa unpredictable trước attacker.

## <a id="threadlocal-random-boundary">Boundary Random và ThreadLocalRandom</a>
`Random` là general stateful PRNG. `ThreadLocalRandom` tránh share một generator state giữa các thread và hữu ích trong concurrent code, nhưng curriculum concurrency đầy đủ thuộc module concurrency. Không cái nào trở thành security-safe chỉ vì output có vẻ random.

## <a id="random-not-security">Vì sao PRNG thông thường không dùng cho security</a>
Security token, key, salt và nonce cần cryptographically strong source. Seed/state predictable có thể làm lộ output tương lai. Dùng `SecureRandom` cho security-sensitive randomness và chuyển threat model sâu hơn sang security-cryptography module.
