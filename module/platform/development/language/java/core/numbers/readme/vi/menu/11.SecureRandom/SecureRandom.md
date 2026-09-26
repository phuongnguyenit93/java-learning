# SecureRandom

Khi randomness là một phần của security contract, tiêu chí không chỉ là “trông random” mà là **attacker khó dự đoán internal state và output**.

`SecureRandom` là cryptographically strong random source cho những use case phù hợp.

## <a id="secure-random-purpose">Mục đích của SecureRandom</a>

Các use case điển hình:

- security token;
- nonce khi protocol yêu cầu random nonce;
- salt;
- secret material;
- input cho key generation khi API/protocol yêu cầu random source.

Ví dụ tạo random bytes:

```java
SecureRandom secureRandom = new SecureRandom();

byte[] bytes = new byte[32];
secureRandom.nextBytes(bytes);
```

### WHY không dùng SecureRandom cho mọi thứ?

Security guarantee có cost/initialization/provider semantics khác ordinary PRNG.

Cho simulation hoặc deterministic test:

```text
reproducibility
→ thường quan trọng

cryptographic unpredictability
→ không phải requirement
```

Khi đó ordinary pseudo-random generator có thể phù hợp hơn.

## <a id="entropy-seeding">Entropy và Seeding</a>

Mental model:

```text
entropy source
    ↓
seed / internal state khó đoán
    ↓
cryptographic PRNG
    ↓
output khó dự đoán
```

### Default application rule

Trong application code thông thường:

```java
SecureRandom secureRandom = new SecureRandom();
```

rồi để platform/provider quản lý seeding thường an toàn hơn tự tạo seed.

### Pitfall: weak manual seed

```java
SecureRandom secureRandom = new SecureRandom();
secureRandom.setSeed(System.currentTimeMillis()); // đừng dùng timestamp như nguồn entropy chính
```

Nuance quan trọng: `setSeed` **supplement** seed/state đã có, nên repeated calls không tự làm giảm randomness của một instance đã được seed tốt. Nhưng với PRNG `SecureRandom` mới tạo, nếu gọi `setSeed` **trước lần `nextBytes`/`reseed` đầu tiên**, implementation sẽ không thực hiện automatic self-seeding; lúc đó caller phải bảo đảm seed cung cấp đủ entropy.

Vì vậy timestamp hoặc output từ ordinary `Random` không nên được dùng như nguồn entropy chính để khởi tạo security-sensitive generator.

Không nên reasoning:

```text
SecureRandom class
→ mọi seed đều tự động secure
```

Security property phụ thuộc toàn bộ entropy/state lifecycle, không chỉ tên class.

### `getInstanceStrong` không phải mặc định bắt buộc

`SecureRandom.getInstanceStrong()` có thể chọn algorithm/provider với stronger platform-specific characteristics, nhưng có thể có latency/blocking/property khác.

Rule thực tế:

```text
default SecureRandom
→ thường đủ cho application use case

getInstanceStrong()
→ chỉ dùng khi requirement cụ thể cần contract đó
```

## <a id="security-boundary">Ranh giới Security/Cryptography</a>

Numbers chỉ cần giúp learner hiểu:

```text
Random
→ deterministic pseudo-random contract
→ testing/simulation/general randomness

SecureRandom
→ attacker-oriented unpredictability contract
→ security-sensitive randomness
```

Những chủ đề sâu hơn như:

- key-size selection;
- cipher mode;
- nonce uniqueness requirement;
- IV construction;
- provider configuration;
- entropy source internals;
- cryptographic protocol design;

thuộc module security/cryptography.

Đặc biệt, không nên kết luận rằng dùng `SecureRandom` là đủ để thiết kế crypto an toàn. `SecureRandom` chỉ giải quyết **random source**; protocol còn nhiều invariant khác.

Sau module Numbers, câu hỏi quan trọng khi gặp một con số là:

> representation và policy nào phù hợp với contract của bài toán: range, exactness, decimal semantics, rounding hay unpredictability?
