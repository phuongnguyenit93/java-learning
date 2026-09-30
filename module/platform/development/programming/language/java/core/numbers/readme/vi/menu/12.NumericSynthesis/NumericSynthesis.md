# Chọn mô hình số phù hợp

Biết từng API số riêng lẻ vẫn chưa đủ. Trong mã nguồn thực tế, lỗi thường xuất hiện vì cách biểu diễn hoặc chính sách xử lý được chọn theo thói quen thay vì dựa trên yêu cầu của bài toán.

Chương này kết nối toàn bộ mô-đun thành một quá trình ra quyết định:

```text
yêu cầu của bài toán
        ↓
chọn cách biểu diễn số
        ↓
nhận diện cách nó có thể gây sai lệch hoặc thất bại
        ↓
làm rõ quy tắc so sánh / tràn số / làm tròn
        ↓
kiểm tra ranh giới với các mô-đun liên quan
```

Mục tiêu không phải ghi nhớ một kiểu số "tốt nhất" cho mọi trường hợp. Mục tiêu là giải thích được **vì sao cách biểu diễn và các chính sách này phù hợp với giá trị đang xử lý**.

## <a id="numeric-decision-model">Mô hình ra quyết định cho giá trị số</a>

Hãy bắt đầu từ ý nghĩa của giá trị, không bắt đầu từ tên kiểu Java.

```text
Giá trị luôn là số nguyên?
        │
        ├─ có → phạm vi hợp lệ có nằm trong int/long?
        │          ├─ có    → số nguyên độ rộng cố định
        │          └─ không → BigInteger
        │
        └─ không → bài toán có yêu cầu ngữ nghĩa thập phân chính xác?
                     ├─ có    → BigDecimal
                     └─ không → float/double, thường ưu tiên double
```

Sau lựa chọn đầu tiên, cần hỏi tiếp ngay:

```text
số nguyên độ rộng cố định
→ điều gì xảy ra nếu vượt phạm vi?
→ có chấp nhận giá trị quay vòng hay phải phát hiện tràn số?

số dấu phẩy động
→ mức sai số xấp xỉ nào được chấp nhận?
→ quy tắc so sánh theo sai số được xác định thế nào?

BigInteger
→ chi phí bộ nhớ và tính toán tăng thêm có phù hợp không?
→ điều gì xảy ra khi chuyển ngược về kiểu nguyên thủy?

BigDecimal
→ scale và precision có ý nghĩa gì trong nghiệp vụ này?
→ được phép làm tròn ở đâu?
→ định danh dựa trên cách biểu diễn hay giá trị số học?
```

Thói quen quan trọng là luôn ghép mỗi cách biểu diễn với **kiểu sai lệch hoặc giới hạn đặc trưng của nó**.

## <a id="numeric-policy-boundaries">Cách biểu diễn và chính sách xử lý là hai quyết định khác nhau</a>

Chọn đúng kiểu dữ liệu chưa kết thúc việc thiết kế.

Ví dụ, yêu cầu sau vẫn chưa đầy đủ:

```text
"Tiền dùng BigDecimal"
```

Nghiệp vụ vẫn có thể phải quy định:

```text
scale của dữ liệu đầu vào
precision trung gian
RoundingMode
thời điểm làm tròn
quy tắc bằng nhau / chuẩn hóa
ranh giới lưu trữ dữ liệu
```

Tương tự, chọn `long` chưa trả lời việc tràn số nên được phép quay vòng, phải phát sinh lỗi hay phải được chặn bằng kiểm tra điều kiện đầu vào. Chọn `double` chưa xác định sai số so sánh. Chọn `SecureRandom` cũng chưa định nghĩa một giao thức mật mã an toàn.

Hãy tách thành hai lớp:

```text
cách biểu diễn
→ biểu diễn được những giá trị nào và phép toán tuân theo mô hình nào?

chính sách xử lý
→ ứng dụng yêu cầu hành vi gì tại những ranh giới có rủi ro?
```

Một số vấn đề sâu hơn thuộc trách nhiệm chính của mô-đun khác:

```text
quy tắc equals / hashCode / thứ tự
→ Object Contract

hành vi của cấu trúc dữ liệu dựa trên hash hoặc có sắp xếp
→ Collection

định dạng số / tiền tệ theo ngôn ngữ và khu vực
→ Localization

khóa / IV / nonce / mã hóa / chữ ký
→ Security & Cryptography
```

Numbers cần giải thích hệ quả liên quan đến số rồi chuyển tiếp sang mô-đun sở hữu chủ đề sâu hơn, thay vì lặp lại toàn bộ kiến thức của mô-đun khác.

## <a id="numeric-synthesis-cases">Ví dụ tổng hợp từ đầu đến cuối</a>

### Bộ đếm có giới hạn biết trước

Yêu cầu:

```text
số nguyên
giá trị tối đa nhỏ hơn 2 tỷ
tràn số đồng nghĩa với lỗi chương trình
```

Lập luận:

```text
int
→ phạm vi đủ dùng
→ phép toán chính xác trong phạm vi đó
→ kiểm tra giới hạn hoặc dùng phép toán có kiểm tra khi không được phép tràn số
```

### Số đo khoa học

Yêu cầu:

```text
có phần thập phân
phạm vi động lớn
chấp nhận sai số biểu diễn nhỏ
```

Lập luận:

```text
double
→ chấp nhận biểu diễn nhị phân xấp xỉ
→ so sánh theo chiến lược sai số do bài toán quy định
→ xử lý NaN / vô cực nếu đầu vào hoặc phép toán có thể tạo ra chúng
```

### Tính toán tiền tệ

Yêu cầu:

```text
phải giữ đúng ý nghĩa thập phân
quy tắc làm tròn là một phần của nghiệp vụ
```

Lập luận:

```text
BigDecimal
→ khởi tạo từ ý nghĩa thập phân, không từ giá trị nhị phân đã có sai số
→ xác định scale / precision / ranh giới làm tròn
→ quyết định bằng nhau dựa trên cách biểu diễn hay giá trị số học
```

### Số nguyên vượt long

Yêu cầu:

```text
số nguyên
giá trị hợp lệ có thể vượt long
```

Lập luận:

```text
BigInteger
→ không có giới hạn tràn số độ rộng cố định đối với giá trị hợp lệ
→ phép toán dùng phương thức và trả về đối tượng mới
→ chuyển về kiểu nguyên thủy trở thành một ranh giới thu hẹp cần kiểm soát
```

### Giá trị ngẫu nhiên liên quan đến bảo mật

Yêu cầu:

```text
kẻ tấn công không được có khả năng dự đoán đầu ra tiếp theo
```

Lập luận:

```text
SecureRandom
→ tính khó dự đoán là một phần của yêu cầu
→ trong mã ứng dụng thông thường, để nền tảng/nhà cung cấp bảo mật (provider) quản lý việc khởi tạo seed
→ thiết kế giao thức mật mã được chuyển sang Security & Cryptography
```

Danh sách kiểm tra cuối cùng khi xử lý số:

```text
1. Giá trị này mang ý nghĩa gì?
2. Phạm vi hợp lệ là bao nhiêu?
3. Cần chính xác theo số nguyên, chính xác theo thập phân hay chỉ cần xấp xỉ?
4. Kiểu sai lệch hoặc lỗi nào được chấp nhận?
5. Quy tắc so sánh nào là đúng?
6. Được phép làm tròn ở đâu?
7. Việc chuyển đổi có đi qua ranh giới làm mất thông tin không?
8. Ngẫu nhiên chỉ phục vụ thống kê/tái lập hay liên quan đến bảo mật?
9. Vấn đề tiếp theo có thuộc một mô-đun Java khác không?
```

Khi trả lời được rõ ràng các câu hỏi này, hành vi xử lý số đang được thiết kế có chủ đích thay vì được lựa chọn theo phỏng đoán.
