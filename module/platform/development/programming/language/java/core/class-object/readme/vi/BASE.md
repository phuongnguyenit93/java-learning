# Class và Object

Module này xây nền tảng cho cách Java mô hình hóa **type, instance, state và object lifecycle**. Trọng tâm là hiểu class định nghĩa cấu trúc/hành vi, object là instance runtime và các rule khởi tạo quyết định state ban đầu như thế nào.

## Learning flow

1. class và object;
2. constructor;
3. `this` và `super`;
4. access modifier;
5. `static` và `final`;
6. initialization blocks;
7. initialization order;
8. object creation lifecycle;
9. nested/inner classes;
10. `Object` class;
11. enum;
12. copy semantics;
13. aliasing và mutability;
14. immutability và defensive copy;
15. tổng hợp class/object design.

## Mental model cần giữ

Một object không chỉ là “biến chứa dữ liệu”. Cần theo được type declaration → initialization → reference/aliasing → mutation/copy → object-level contracts. Equality/hash/order được tách sang Object Contract; inheritance/polymorphism ở mức design được học sâu trong OOP.
