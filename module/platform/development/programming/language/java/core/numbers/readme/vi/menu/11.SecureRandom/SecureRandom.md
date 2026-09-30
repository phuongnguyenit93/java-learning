# SecureRandom và yêu cầu bảo mật

Khi tính ngẫu nhiên là một phần của yêu cầu bảo mật, tiêu chí không chỉ là “trông ngẫu nhiên” mà là **kẻ tấn công khó dự đoán trạng thái nội bộ và đầu ra**.

`SecureRandom` là nguồn ngẫu nhiên mạnh về mặt mật mã cho những trường hợp sử dụng phù hợp.

## <a id="secure-random-purpose">Mục đích của SecureRandom</a>

Các trường hợp sử dụng điển hình:

- token bảo mật;
- nonce khi giao thức yêu cầu nonce ngẫu nhiên;
- salt;
- dữ liệu bí mật;
- đầu vào cho quá trình sinh khóa khi API/giao thức yêu cầu nguồn ngẫu nhiên.

Ví dụ tạo các byte ngẫu nhiên:

```java
SecureRandom secureRandom = new SecureRandom();

byte[] bytes = new byte[32];
secureRandom.nextBytes(bytes);
```

### VÌ SAO không dùng SecureRandom cho mọi thứ?

Đảm bảo bảo mật có chi phí, cách khởi tạo và ngữ nghĩa của nhà cung cấp bảo mật (provider) khác với bộ sinh số giả ngẫu nhiên thông thường.

Với mô phỏng hoặc kiểm thử cần tính xác định:

```text
khả năng tái lập kết quả
→ thường quan trọng

tính khó dự đoán về mặt mật mã
→ không phải yêu cầu
```

Khi đó bộ sinh số giả ngẫu nhiên thông thường có thể phù hợp hơn.

## <a id="entropy-seeding">Entropy và khởi tạo seed</a>

Cách ghi nhớ:

```text
nguồn entropy
    ↓
seed / trạng thái nội bộ khó đoán
    ↓
PRNG dùng cho mật mã
    ↓
đầu ra khó dự đoán
```

### Quy tắc mặc định cho ứng dụng

Trong mã ứng dụng thông thường:

```java
SecureRandom secureRandom = new SecureRandom();
```

rồi để nền tảng/nhà cung cấp bảo mật (provider) quản lý việc khởi tạo seed thường an toàn hơn tự tạo seed.

### Lỗi thường gặp: tự cấp seed yếu

```java
SecureRandom secureRandom = new SecureRandom();
secureRandom.setSeed(System.currentTimeMillis()); // đừng dùng timestamp như nguồn entropy chính
```

Điểm quan trọng: `setSeed` **bổ sung** vào seed/trạng thái đã có, nên gọi lặp lại không tự làm giảm tính ngẫu nhiên của một đối tượng đã được khởi tạo seed tốt. Nhưng với PRNG `SecureRandom` mới tạo, nếu gọi `setSeed` **trước lần `nextBytes`/`reseed` đầu tiên**, phần triển khai sẽ không thực hiện tự khởi tạo seed; lúc đó bên gọi phải bảo đảm seed cung cấp đủ entropy.

Vì vậy mốc thời gian hoặc đầu ra từ `Random` thông thường không nên được dùng làm nguồn entropy chính để khởi tạo bộ sinh dùng cho mục đích nhạy cảm về bảo mật.

Không nên suy luận:

```text
lớp SecureRandom
→ mọi seed đều tự động an toàn
```

Thuộc tính bảo mật phụ thuộc vào toàn bộ vòng đời entropy/trạng thái, không chỉ tên lớp.

### `getInstanceStrong` không phải mặc định bắt buộc

`SecureRandom.getInstanceStrong()` có thể chọn thuật toán/nhà cung cấp bảo mật (provider) với các đặc tính mạnh hơn tùy nền tảng, nhưng có thể có độ trễ, khả năng chặn hoặc đặc tính vận hành khác.

Quy tắc thực tế:

```text
SecureRandom mặc định
→ thường đủ cho trường hợp sử dụng của ứng dụng

getInstanceStrong()
→ chỉ dùng khi yêu cầu cụ thể cần đặc tính đó
```

## <a id="security-boundary">Ranh giới bảo mật và mật mã học</a>

Numbers chỉ cần giúp người học hiểu:

```text
Random
→ cơ chế giả ngẫu nhiên có tính xác định
→ kiểm thử/mô phỏng/tính ngẫu nhiên thông thường

SecureRandom
→ yêu cầu khó dự đoán trước kẻ tấn công
→ tính ngẫu nhiên nhạy cảm về bảo mật
```

Những chủ đề sâu hơn như:

- lựa chọn kích thước khóa;
- chế độ mã hóa (cipher mode);
- yêu cầu nonce không trùng lặp;
- cách tạo IV;
- cấu hình nhà cung cấp bảo mật (provider);
- chi tiết bên trong của nguồn entropy;
- thiết kế giao thức mật mã;

thuộc mô-đun Bảo mật và Mật mã học.

Đặc biệt, không nên kết luận rằng dùng `SecureRandom` là đủ để thiết kế hệ thống mật mã an toàn. `SecureRandom` chỉ giải quyết **nguồn ngẫu nhiên**; giao thức còn nhiều điều kiện bất biến khác.

Chương tổng hợp cuối cùng sẽ gom các lựa chọn trong Numbers lại thành một mô hình ra quyết định. Câu hỏi quan trọng khi gặp một con số là:

> cách biểu diễn và chính sách nào phù hợp với yêu cầu của bài toán: phạm vi, độ chính xác, ngữ nghĩa thập phân, làm tròn hay tính khó dự đoán?
