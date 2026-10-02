# Functional Programming trong Java

Module này giới thiệu cách Java biểu diễn **behavior như value** thông qua functional interface, lambda và method reference, rồi dùng composition để xây pipeline xử lý dữ liệu có kiểm soát.

## Learning flow

1. vì sao functional style hữu ích và mental model cốt lõi;
2. functional-interface contract và target typing;
3. lambda;
4. variable capture, scope và state;
5. method reference;
6. standard functional interfaces;
7. behavior composition và higher-order usage;
8. side effect, immutability và `Optional`;
9. boundary với Stream API;
10. trade-off, pitfalls và synthesis.

## Vì sao nên học?

Mục tiêu không phải biến Java thành ngôn ngữ thuần functional. Learner cần biết khi nào higher-order behavior làm code rõ hơn, khi nào mutable state/capture gây khó reasoning và cách các abstraction này trở thành nền tảng cho Stream và nhiều framework API.

## Phạm vi module

Module tập trung vào functional model của Java. Stream được dùng như integration boundary; concurrency/parallel execution và reactive programming là các chủ đề riêng.
