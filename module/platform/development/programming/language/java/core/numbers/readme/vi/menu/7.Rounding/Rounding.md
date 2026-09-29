# Rounding

Rounding xuất hiện khi exact result không thể hoặc không được phép giữ nguyên trong representation mong muốn. Với financial/domain code, rounding là **business policy**, không phải chi tiết format ở bước cuối.

## <a id="rounding-modes">RoundingMode</a>

### WHAT — các mode đang trả lời câu hỏi gì?

Khi bỏ bớt digit, Java phải quyết định result sẽ đi về phía nào.

| Mode | Mental model |
| --- | --- |
| `UP` | đi xa zero |
| `DOWN` | đi về zero |
| `CEILING` | đi về +∞ |
| `FLOOR` | đi về -∞ |
| `HALF_UP` | chọn nearest; nếu tie → đi xa zero |
| `HALF_DOWN` | chọn nearest; nếu tie → đi về zero |
| `HALF_EVEN` | chọn nearest; nếu tie → chọn neighbor có last digit chẵn |
| `UNNECESSARY` | không cho phép rounding; nếu cần round thì fail |

Ở đây **tie** nghĩa là exact value nằm đúng giữa hai candidate sau khi round, ví dụ `2.5` khi làm tròn về số nguyên. Nếu không phải tie, các mode `HALF_*` đều chọn candidate gần hơn.

### WHY cần phân biệt UP/DOWN với CEILING/FLOOR?

Với số dương chúng có thể nhìn giống nhau, nhưng với số âm thì khác:

```text
value = -2.1

UP      → -3   (xa zero)
DOWN    → -2   (về zero)
CEILING → -2   (về +∞)
FLOOR   → -3   (về -∞)
```

Đây là lý do không nên chọn rounding mode dựa vào tên nghe “hợp lý”; phải hiểu direction contract.

### Tie case

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

`HALF_EVEN` giúp giảm aggregate bias trong một số workload có nhiều tie, nhưng không có nghĩa nó luôn đúng cho mọi domain.

Với số âm, same tie rules vẫn áp dụng theo direction của từng mode. Ví dụ `-2.5` với `HALF_UP` thành `-3`, còn `HALF_DOWN` và `HALF_EVEN` thành `-2`.

## <a id="rounding-at-boundaries">Khi nào Rounding xảy ra?</a>

Rounding có thể xảy ra khi:

- giảm scale bằng `setScale`;
- dùng `MathContext` với precision giới hạn;
- divide một decimal không có terminating representation;
- convert result về đơn vị domain có granularity cố định;
- áp dụng legal/business rule.

Ví dụ:

```java
BigDecimal tax =
        new BigDecimal("10.235")
                .setScale(2, RoundingMode.HALF_UP);

System.out.println(tax); // 10.24
```

### Round ở boundary có chủ ý

Giả sử:

```text
price × quantity
        ↓
subtotal
        ↓
discount
        ↓
tax
        ↓
final payable amount
```

Nếu round sau **mỗi operation**, accumulated result có thể khác với việc giữ intermediate precision rồi chỉ round ở domain boundary cuối.

Vì vậy policy phải trả lời:

```text
round cái gì?
round lúc nào?
round tới scale nào?
round bằng mode nào?
```

### Double rounding

Round nhiều lần có thể khác round một lần tới target cuối.

```text
2.445

round 2 decimals HALF_UP
→ 2.45

then round 1 decimal HALF_UP
→ 2.5

nhưng round trực tiếp 1 decimal từ 2.445
→ 2.4
```

Đây là lý do intermediate rounding phải được domain quyết định, không phải thêm tùy tiện.

## <a id="financial-rounding-policy">Chính sách làm tròn</a>

Với money/tax/rate, policy thường phải xác định:

- currency scale;
- line-item hay final-total rounding;
- rounding mode;
- intermediate precision;
- handling khi exact result không thể biểu diễn ở target scale.

Ví dụ hai policy:

```text
Policy A
→ round từng line item
→ cộng các value đã round

Policy B
→ cộng exact/intermediate values
→ round final total
```

Hai implementation đều có thể dùng BigDecimal đúng API nhưng vẫn cho result khác nếu policy khác nhau.

### `UNNECESSARY` như assertion

```java
BigDecimal exact =
        new BigDecimal("10.00")
                .setScale(2, RoundingMode.UNNECESSARY);
```

`UNNECESSARY` hữu ích khi contract nói rằng rounding **không được phép xảy ra**. Nếu operation cần bỏ digit, Java throw `ArithmeticException`.

Chương tiếp theo giải quyết một subtlety khác của BigDecimal: **same numerical value chưa chắc `equals` nhau vì scale có thể khác**.
