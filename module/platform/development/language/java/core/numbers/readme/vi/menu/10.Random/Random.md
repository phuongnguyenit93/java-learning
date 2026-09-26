# Random

`Random` không tạo “ngẫu nhiên tuyệt đối”. Nó tạo một chuỗi **pseudo-random** từ trạng thái/seed theo algorithm xác định.

## <a id="pseudo-random-model">Pseudo-random và Seed</a>

Cùng seed và cùng chuỗi lời gọi có thể tạo cùng kết quả:

```java
Random a = new Random(42);
Random b = new Random(42);
```

Determinism này rất hữu ích cho test, simulation và reproducible experiment.

Seed không phải “mật khẩu bảo mật”; với PRNG thông thường, đầu ra có thể dự đoán nếu trạng thái/algorithm bị suy ra.

## <a id="threadlocal-random-boundary">Random và ThreadLocalRandom</a>

`ThreadLocalRandom` được thiết kế cho concurrent mã để tránh contention khi nhiều thread cùng cần pseudo-random values.

Nó phù hợp cho các use case như randomized scheduling, simulation hoặc sampling không có security yêu cầu.

Không nên chọn `ThreadLocalRandom` chỉ vì tên có “thread”; câu hỏi vẫn là concurrency/performance hợp đồng của use case.

## <a id="random-not-security">Random không dành cho Security</a>

Token, session id, secret, nonce bảo mật hoặc key material cần unpredictability mạnh hơn PRNG thông thường.

`Random` và `ThreadLocalRandom` **không được thiết kế làm cryptographic random source**.

Nếu đầu ra phải khó đoán trước đối với kẻ tấn công, chương tiếp theo chuyển sang `SecureRandom`.
