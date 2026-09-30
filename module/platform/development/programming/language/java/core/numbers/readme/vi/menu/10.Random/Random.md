# Sinh số giả ngẫu nhiên

`Random` không tạo “ngẫu nhiên tuyệt đối”. Nó tạo một chuỗi **giả ngẫu nhiên (pseudo-random)** từ trạng thái nội bộ/giá trị khởi tạo (seed) theo một thuật toán xác định.

Điểm quan trọng là phân biệt:

```text
phân phối/tính ngẫu nhiên hữu ích cho bài toán
và
tính khó dự đoán phục vụ bảo mật
```

Hai nhóm yêu cầu này không giống nhau.

## <a id="pseudo-random-model">Số giả ngẫu nhiên và seed</a>

### KHÁI NIỆM

Có thể hình dung một bộ sinh số giả ngẫu nhiên như sau:

```text
seed / trạng thái nội bộ
        ↓
thuật toán xác định
        ↓
chuỗi giá trị
```

Cùng seed và cùng thứ tự gọi có thể tạo cùng đầu ra:

```java
Random a = new Random(42);
Random b = new Random(42);

System.out.println(a.nextInt(1000));
System.out.println(b.nextInt(1000));
// cùng kết quả nếu trạng thái/thứ tự gọi giống nhau
```

### VÌ SAO tính xác định lại hữu ích?

Trong kiểm thử/mô phỏng:

```text
dữ liệu trông ngẫu nhiên
        +
seed có thể tái lập
        =
lỗi có thể tái hiện
```

Kiểm thử có thể truyền seed cố định để cùng đầu vào tạo cùng chuỗi giá trị giữa các lần chạy.

### Sinh số ngẫu nhiên trong phạm vi

```java
int value = random.nextInt(100);
```

cho kết quả trong phạm vi:

```text
0 <= value < 100
```

Ưu tiên API có giới hạn phạm vi thay vì tự làm:

```java
random.nextInt() % 100
```

vì phép modulo thủ công có thể tạo kết quả âm và làm sai phân phối nếu xử lý không đúng.

## <a id="threadlocal-random-boundary">Random và ThreadLocalRandom</a>

`ThreadLocalRandom` được thiết kế cho mã chạy đồng thời để giảm tranh chấp tài nguyên (contention) khi nhiều luồng cần giá trị giả ngẫu nhiên.

```java
int value =
        ThreadLocalRandom.current()
                .nextInt(10, 20);
```

Nó phù hợp cho:

- mô phỏng;
- lập lịch có yếu tố ngẫu nhiên;
- lấy mẫu;
- heuristic cân bằng tải không có yêu cầu bảo mật.

`ThreadLocalRandom` không có nghĩa “mọi mã chạy trên luồng nên dùng nó”. Câu hỏi đúng là:

```text
trường hợp sử dụng có tranh chấp khi chạy đồng thời không?
và
tính ngẫu nhiên có cần khó dự đoán để phục vụ bảo mật không?
```

Dự án dùng Java 21 làm phiên bản nền, vì vậy API sinh số ngẫu nhiên hiện đại `java.util.random.RandomGenerator` và nhiều cách triển khai bộ sinh đã sẵn có. Ở mô-đun Numbers, chỉ cần hiểu rằng **lựa chọn thuật toán, khả năng tái lập kết quả và mô hình chạy đồng thời là những mối quan tâm riêng biệt**; chi tiết chọn từng thuật toán thuộc mức sâu hơn.

`Math.random()` cũng chỉ là tiện ích giả ngẫu nhiên cho trường hợp sử dụng thông thường; nó không làm cho nguồn ngẫu nhiên trở nên an toàn về mặt mật mã.

## <a id="random-not-security">Random không dành cho bảo mật</a>

`Random` và `ThreadLocalRandom` không được thiết kế để chống kẻ tấn công dự đoán trạng thái/đầu ra.

Không dùng chúng cho:

- token đặt lại mật khẩu;
- bí mật của phiên;
- token xác thực;
- nonce mật mã;
- vật liệu tạo khóa;
- định danh nhạy cảm về bảo mật nếu tính khó dự đoán là một yêu cầu.

### Seed không phải mật khẩu

```java
new Random(System.currentTimeMillis())
```

không biến bộ sinh thành một nguồn an toàn. Mốc thời gian có khả năng dự đoán/không gian tìm kiếm khác hoàn toàn nguồn entropy mạnh.

Nếu kẻ tấn công không được phép dự đoán đầu ra tiếp theo, chương sau chuyển sang `SecureRandom`.
