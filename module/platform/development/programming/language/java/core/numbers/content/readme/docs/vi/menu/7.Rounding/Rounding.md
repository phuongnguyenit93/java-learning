# Chính sách làm tròn

Làm tròn xuất hiện khi kết quả chính xác không thể hoặc không được phép giữ nguyên trong cách biểu diễn mong muốn. Với mã xử lý tài chính/nghiệp vụ, làm tròn là **chính sách nghiệp vụ**, không phải chi tiết định dạng ở bước cuối.

## <a id="rounding-modes">RoundingMode</a>

### KHÁI NIỆM — các chế độ đang trả lời câu hỏi gì?

Khi bỏ bớt chữ số, Java phải quyết định kết quả sẽ đi về phía nào.

| Chế độ | Cách hiểu |
| --- | --- |
| `UP` | đi xa số 0 |
| `DOWN` | đi về số 0 |
| `CEILING` | đi về +∞ |
| `FLOOR` | đi về -∞ |
| `HALF_UP` | chọn giá trị gần nhất; nếu ở chính giữa → đi xa số 0 |
| `HALF_DOWN` | chọn giá trị gần nhất; nếu ở chính giữa → đi về số 0 |
| `HALF_EVEN` | chọn giá trị gần nhất; nếu ở chính giữa → chọn giá trị lân cận có chữ số cuối chẵn |
| `UNNECESSARY` | không cho phép làm tròn; nếu cần làm tròn thì báo lỗi |

Ở đây **tie** là thuật ngữ chỉ trường hợp giá trị chính xác nằm đúng giữa hai giá trị ứng viên sau khi làm tròn, ví dụ `2.5` khi làm tròn về số nguyên. Nếu không phải tie, các chế độ `HALF_*` đều chọn giá trị gần hơn.

### VÌ SAO cần phân biệt UP/DOWN với CEILING/FLOOR?

Với số dương chúng có thể nhìn giống nhau, nhưng với số âm thì khác:

```text
value = -2.1

UP      → -3   (xa số 0)
DOWN    → -2   (về số 0)
CEILING → -2   (về +∞)
FLOOR   → -3   (về -∞)
```

Đây là lý do không nên chọn chế độ làm tròn chỉ dựa vào tên nghe “hợp lý”; phải hiểu quy tắc về hướng làm tròn.

### Trường hợp ở chính giữa (tie)

Với một chữ số nguyên:

```text
 2.5

HALF_UP   → 3
HALF_DOWN → 2
HALF_EVEN → 2

 3.5

HALF_UP   → 4
HALF_DOWN → 3
HALF_EVEN → 4
```

`HALF_EVEN` giúp giảm độ lệch tích lũy trong một số khối lượng tính toán có nhiều trường hợp tie, nhưng không có nghĩa nó luôn đúng cho mọi bài toán.

Với số âm, các quy tắc tie tương tự vẫn áp dụng theo hướng của từng chế độ. Ví dụ `-2.5` với `HALF_UP` thành `-3`, còn `HALF_DOWN` và `HALF_EVEN` thành `-2`.

## <a id="rounding-at-boundaries">Khi nào việc làm tròn xảy ra?</a>

Việc làm tròn có thể xảy ra khi:

- giảm scale bằng `setScale`;
- dùng `MathContext` với độ chính xác giới hạn;
- chia một số thập phân không có biểu diễn hữu hạn;
- chuyển kết quả về đơn vị nghiệp vụ có bước giá trị cố định;
- áp dụng quy định pháp lý/quy tắc nghiệp vụ.

Ví dụ:

```java
BigDecimal tax =
        new BigDecimal("10.235")
                .setScale(2, RoundingMode.HALF_UP);

System.out.println(tax); // 10.24
```

### Làm tròn tại ranh giới có chủ ý

Giả sử:

```text
giá × số lượng
        ↓
tạm tính
        ↓
chiết khấu
        ↓
thuế
        ↓
số tiền cuối cùng phải trả
```

Nếu làm tròn sau **mỗi phép toán**, kết quả tích lũy có thể khác với việc giữ độ chính xác trung gian rồi chỉ làm tròn tại ranh giới nghiệp vụ cuối cùng.

Vì vậy chính sách phải trả lời:

```text
làm tròn cái gì?
làm tròn lúc nào?
làm tròn tới scale nào?
làm tròn bằng chế độ nào?
```

### Làm tròn hai lần

Làm tròn nhiều lần có thể khác làm tròn một lần tới đích cuối.

```text
2.445

làm tròn 2 chữ số thập phân với HALF_UP
→ 2.45

sau đó làm tròn 1 chữ số thập phân với HALF_UP
→ 2.5

nhưng làm tròn trực tiếp 1 chữ số thập phân từ 2.445
→ 2.4
```

Đây là lý do việc làm tròn trung gian phải do yêu cầu bài toán quyết định, không phải thêm tùy tiện.

## <a id="financial-rounding-policy">Chính sách làm tròn</a>

Với tiền/thuế/tỷ lệ, chính sách thường phải xác định:

- scale của tiền tệ;
- làm tròn từng dòng hay tổng cuối;
- chế độ làm tròn;
- độ chính xác trung gian;
- cách xử lý khi kết quả chính xác không thể biểu diễn ở scale đích.

Ví dụ hai chính sách:

```text
Chính sách A
→ làm tròn từng dòng
→ cộng các giá trị đã làm tròn

Chính sách B
→ cộng các giá trị chính xác/trung gian
→ làm tròn tổng cuối
```

Hai cách triển khai đều có thể dùng BigDecimal đúng API nhưng vẫn cho kết quả khác nếu chính sách khác nhau.

### `UNNECESSARY` như một phép khẳng định điều kiện

```java
BigDecimal exact =
        new BigDecimal("10.00")
                .setScale(2, RoundingMode.UNNECESSARY);
```

`UNNECESSARY` hữu ích khi yêu cầu nói rằng việc làm tròn **không được phép xảy ra**. Nếu phép toán cần bỏ chữ số, Java ném `ArithmeticException`.

Chương tiếp theo giải quyết một điểm khác biệt khác của BigDecimal: **cùng giá trị số chưa chắc `equals` nhau vì scale có thể khác**.
