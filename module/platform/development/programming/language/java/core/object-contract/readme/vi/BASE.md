# Object Contract

Module này tập trung vào các contract quyết định object **được nhận diện, so sánh, hash, biểu diễn và sắp thứ tự** như thế nào trong Java.

## Learning flow

1. vì sao object contract tồn tại;
2. identity và equality;
3. `equals`;
4. `hashCode`;
5. contract giữa `equals` và `hashCode`;
6. `toString`;
7. `Comparable`;
8. `Comparator`;
9. tổng hợp contract design.

## Vì sao nên học?

Một implementation `equals` hoặc `hashCode` sai có thể làm Collection behavior sai dù code vẫn compile. Ordering cũng cần contract nhất quán với domain requirement, không chỉ “sort được”.

Class/Object module cung cấp object foundation; Collection là nơi các contract này trở nên đặc biệt quan trọng. Mục tiêu cuối cùng là thiết kế equality/hash/order có chủ đích và kiểm chứng được.
