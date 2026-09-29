# Random

`Random` không tạo “random tuyệt đối”. Nó tạo một chuỗi **pseudo-random** từ internal state/seed theo algorithm xác định.

Điểm quan trọng là phân biệt:

```text
distribution/useful randomness
và
security unpredictability
```

Hai contract này không giống nhau.

## <a id="pseudo-random-model">Pseudo-random và Seed</a>

### WHAT

Một pseudo-random generator có thể hình dung:

```text
seed / internal state
        ↓
deterministic algorithm
        ↓
sequence of values
```

Cùng seed và cùng call sequence có thể tạo cùng output:

```java
Random a = new Random(42);
Random b = new Random(42);

System.out.println(a.nextInt(1000));
System.out.println(b.nextInt(1000));
// cùng result nếu state/call sequence giống nhau
```

### WHY determinism lại hữu ích?

Trong test/simulation:

```text
random-looking data
        +
reproducible seed
        =
bug có thể tái hiện
```

Test có thể inject seed cố định để cùng input tạo cùng sequence giữa các lần chạy.

### Bounded random

```java
int value = random.nextInt(100);
```

cho result trong range:

```text
0 <= value < 100
```

Ưu tiên bounded API thay vì tự làm:

```java
random.nextInt() % 100
```

vì modulo thủ công có thể tạo negative result và distribution issue nếu xử lý không đúng.

## <a id="threadlocal-random-boundary">Random và ThreadLocalRandom</a>

`ThreadLocalRandom` được thiết kế cho concurrent code để giảm contention khi nhiều thread cần pseudo-random values.

```java
int value =
        ThreadLocalRandom.current()
                .nextInt(10, 20);
```

Nó phù hợp cho:

- simulation;
- randomized scheduling;
- sampling;
- load-balancing heuristic không có security contract.

`ThreadLocalRandom` không có nghĩa “mọi code chạy trên thread nên dùng nó”. Câu hỏi đúng là:

```text
use case có concurrent contention không?
và
randomness có cần security unpredictability không?
```

Repository dùng baseline Java 21, vì vậy modern random API `java.util.random.RandomGenerator` và nhiều generator implementation đã sẵn có. Numbers chỉ cần hiểu rằng **algorithm choice, reproducibility và concurrency model là separate concerns**; chi tiết chọn từng algorithm thuộc mức sâu hơn.

`Math.random()` cũng chỉ là tiện ích pseudo-random cho use case thông thường; nó không biến randomness thành cryptographically secure.

## <a id="random-not-security">Random không dành cho Security</a>

`Random` và `ThreadLocalRandom` không được thiết kế để chống attacker dự đoán state/output.

Không dùng chúng cho:

- password reset token;
- session secret;
- authentication token;
- cryptographic nonce;
- key material;
- security-sensitive identifier nếu unpredictability là contract.

### Seed không phải password

```java
new Random(System.currentTimeMillis())
```

không biến generator thành secure. Timestamp có predictability/search space khác hoàn toàn strong entropy.

Nếu attacker không được phép dự đoán output tiếp theo, chương sau chuyển sang `SecureRandom`.
