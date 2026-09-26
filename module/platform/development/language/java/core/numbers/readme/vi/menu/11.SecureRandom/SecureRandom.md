# SecureRandom

Khi randomness là một phần của security hợp đồng, tiêu chí không chỉ là “phân bố trông ngẫu nhiên” mà còn là **khó dự đoán trạng thái/đầu ra đối với kẻ tấn công**.

## <a id="secure-random-purpose">Mục đích của SecureRandom</a>

`SecureRandom` cung cấp cryptographically strong random values phù hợp hơn cho các tình huống như:

- security token;
- nonce;
- salt;
- secret material hoặc đầu vào cho key generation tùy API/protocol.

Nó có cost và initialization characteristics khác `Random`; không cần dùng nó cho mọi simulation hoặc game mechanic không liên quan security.

## <a id="entropy-seeding">Entropy và Seeding</a>

mô hình tư duy quan trọng:

```text
entropy tốt
→ seed/trạng thái khó đoán
        ↓
cryptographic generator
→ đầu ra khó dự đoán
```

Không nên tự tạo seed “bảo mật” bằng timestamp hoặc `Random` rồi đưa vào `SecureRandom`, vì nguồn seed yếu có thể làm giảm security property mong muốn.

Trong đa số trường hợp, để platform/provider quản lý seeding đúng cách là lựa chọn an toàn hơn.

## <a id="security-boundary">Ranh giới Security/Cryptography</a>

Module Numbers chỉ cần hiểu **vì sao `Random` và `SecureRandom` có hợp đồng khác nhau**.

Những chủ đề sâu hơn như:

- key generation;
- cipher/nonce yêu cầu;
- provider;
- entropy source;
- cryptographic protocol;

thuộc module security/cryptography, không nên bị nhồi vào Numbers.

Sau module này, câu hỏi quan trọng nhất khi gặp một con số là:

> cách biểu diễn và chính sách nào phù hợp với hợp đồng của bài toán: phạm vi, exactness, decimal ngữ nghĩa, rounding hay unpredictability?
