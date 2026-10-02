<a id="back-to-top"></a>

# Độ ngẫu nhiên an toàn với SecureRandom

## Menu
- [Vì sao mật mã cần độ ngẫu nhiên khó dự đoán?](#cryptographic-randomness)
- [Entropy và seeding](#entropy-and-seeding)
- [Lựa chọn và sử dụng SecureRandom](#secure-random-selection)
- [Các lỗi thường gặp với randomness trong mật mã](#randomness-misuse)

## <a id="cryptographic-randomness">Vì sao mật mã cần độ ngẫu nhiên khó dự đoán?</a>

<details>
<summary>Nhấn để xem chi tiết</summary>

Nhiều primitive chỉ an toàn khi một số giá trị **khó dự đoán**: key mới, salt, nonce ngẫu nhiên trong những mode yêu cầu random nonce, challenge hoặc token bảo mật.

java.util.Random có thể phù hợp cho simulation hoặc test data nhưng không cung cấp security contract đó. SecureRandom được thiết kế như cryptographically strong RNG.

Điểm cần nhớ không phải “số nhìn có vẻ ngẫu nhiên”, mà là kẻ tấn công không thể thực tế dự đoán đầu ra tiếp theo từ những gì đã quan sát.

~~~java
SecureRandom random = new SecureRandom();
byte[] bytes = new byte[32];
random.nextBytes(bytes);
~~~

Khi một Java crypto API nhận `SecureRandom` trong thao tác khởi tạo hoặc sinh dữ liệu, hãy để provider sử dụng nguồn ngẫu nhiên an toàn phù hợp trừ khi protocol yêu cầu truyền instance cụ thể.
</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="entropy-and-seeding">Entropy và seeding</a>

<details>
<summary>Nhấn để xem chi tiết</summary>

PRNG thường mở rộng một **seed** có entropy thành chuỗi đầu ra dài. Nếu seed đoán được, đầu ra có thể mất tính khó dự đoán dù thuật toán PRNG tốt.

Vì vậy không nên tự seed SecureRandom bằng timestamp, user id hoặc constant:

~~~java
// Không dùng cho bảo mật ở môi trường thực tế.
SecureRandom bad = new SecureRandom();
bad.setSeed(123456789L);
~~~

Với `SecureRandom` dựa trên PRNG, lần sinh đầu ra đầu tiên thường buộc object tự seed từ nguồn entropy của phần triển khai. Nhưng có một chi tiết quan trọng: nếu gọi `setSeed(...)` **trước** `nextBytes(...)` hoặc `reseed(...)`, PRNG không còn tự seed theo bước đó; bên gọi phải bảo đảm seed tự cung cấp có đủ entropy. Sau khi object đã được seed, `setSeed(...)` bổ sung seed material thay vì làm giảm độ ngẫu nhiên hiện có.

Vì vậy không nên “chủ động seed cho chắc” bằng timestamp, user id hay constant. Với code ứng dụng thông thường, để JDK/provider quản lý seeding an toàn hơn việc tự tạo seed dự đoán được.

Trong test cần reproducibility, hãy tách test seam khỏi luồng bảo mật ở môi trường thực tế thay vì làm RNG ở môi trường thực tế trở nên deterministic.
</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="secure-random-selection">Lựa chọn và sử dụng SecureRandom</a>

<details>
<summary>Nhấn để xem chi tiết</summary>

Với phần lớn ứng dụng, new SecureRandom() là điểm bắt đầu hợp lý vì JCA chọn phần triển khai từ provider preference. Khi chính sách bắt buộc một algorithm/provider cụ thể, có thể dùng SecureRandom.getInstance(...).

SecureRandom.getInstanceStrong() yêu cầu một phần triển khai được cấu hình là “strong” cho platform, nhưng “strong” không đồng nghĩa luôn phù hợp hơn cho mọi request path. Một phần triển khai có thể có hành vi về entropy acquisition hoặc latency khác, nên không nên gọi nó theo thói quen trong hot path.

Nguyên tắc thực tế:

- dùng SecureRandom, không dùng Random cho secret;
- reuse instance khi phù hợp thay vì tạo mới liên tục chỉ để “ngẫu nhiên hơn”;
- để provider/JDK quản lý seeding trừ khi protocol có yêu cầu riêng;
- benchmark và quan sát nếu workload tạo lượng lớn random bytes.
</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="randomness-misuse">Các lỗi thường gặp với randomness trong mật mã</a>

<details>
<summary>Nhấn để xem chi tiết</summary>

Các lỗi thường gặp xoay quanh việc nhầm **random-looking** với **security-random**:

- dùng Random hoặc predictable seed cho reset token/key;
- reuse nonce trong mode yêu cầu uniqueness;
- dùng một salt cố định cho mọi password;
- tự tạo key bằng cách lấy substring/hash tùy ý từ dữ liệu dễ đoán;
- log raw random secret để debug.

Randomness cũng không sửa được một primitive dùng sai. Key ngẫu nhiên tốt không làm ECB trở thành authenticated encryption; nonce ngẫu nhiên không thay thế certificate validation.

Khi kiểm tra mã, hãy hỏi riêng hai câu: “giá trị này cần khó dự đoán hay chỉ cần duy nhất?” và “protocol yêu cầu ngẫu nhiên, duy nhất hay cả hai?”. Yêu cầu khác nhau dẫn đến cách sinh và vòng đời khác nhau.
Randomness an toàn thường được dùng để sinh key, nonce và seed; vì vậy bước kế tiếp là phân biệt các loại key và vòng đời của key material.

</details>

- [Quay lại đầu trang](#back-to-top)
