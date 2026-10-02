# Security & Cryptography trong Java

Module này xây dựng mental model để sử dụng các API bảo mật và mật mã học của Java một cách có chủ đích. Trọng tâm không phải học thuộc tên thuật toán, mà là hiểu mục tiêu bảo mật nào cần được giải quyết, primitive nào phù hợp, Java lựa chọn implementation qua JCA/JCE Provider như thế nào, và key/certificate/trust material tham gia vào toàn bộ flow ra sao.

## Điều kiện nên có trước

Learner nên đã nắm Java Core, exception/resource handling và các khái niệm random-number cơ bản. Kiến thức networking giúp dễ liên hệ khi đến JSSE/TLS, nhưng socket và HTTP mechanics không được dạy lại trong module này.

## Learning flow

Module đi từ nền tảng đến integration theo thứ tự:

1. xác định mục tiêu bảo mật và phân biệt encoding, hashing, MAC, encryption, signature;
2. hiểu JCA/JCE, engine classes, algorithm names và Provider architecture;
3. xây dựng mental model về SecureRandom và key material;
4. học integrity/authenticity qua MessageDigest, MAC và digital signature;
5. học confidentiality qua symmetric, asymmetric và authenticated encryption;
6. phân biệt shared-secret establishment với key derivation;
7. quản lý key/certificate material bằng KeyStore và certificate-path trust model;
8. nối các concept trên vào JSSE/TLS;
9. tổng hợp các lựa chọn, constraint và failure pattern thành một flow thiết kế mật mã an toàn.

## Ranh giới module

Module này sở hữu Java security-provider architecture, cryptographic primitives, key APIs, KeyStore/certificate APIs và JSSE/TLS security semantics. Generic random-number concepts thuộc Java Numbers; socket/HTTP mechanics thuộc Java Networking; authentication/authorization framework behavior thuộc các security framework tương ứng; IAM, secret lifecycle và PKI operations ở mức hạ tầng không được mở rộng thành curriculum riêng tại đây.

## Kết quả cần đạt

Sau khi hoàn thành module, learner cần giải thích được từ security goal đến primitive, algorithm/provider, randomness/key material, storage/trust và TLS khi cần; đồng thời nhận diện được các sai lầm như dùng digest thay cho authentication, reuse nonce, bỏ qua certificate validation, hard-code secret hoặc khóa application vào provider không cần thiết.
