# Java Language Basics

Đây là module nền tảng cho **cú pháp, type model và execution semantics cơ bản của Java source code**. Mục tiêu là tạo vocabulary chung trước khi đi vào object model, Generics, Collections, OOP và các API cao hơn.

## Learning flow

1. Java language tồn tại để giải quyết gì;
2. source-code structure;
3. primitive và reference types;
4. variables và scope;
5. `null`;
6. wrapper types và boxing/unboxing;
7. operators;
8. casting;
9. control flow;
10. arrays;
11. methods;
12. varargs;
13. pass-by-value;
14. packages và imports;
15. type-system mental model;
16. tổng hợp nền tảng ngôn ngữ.

## Mental model cần giữ

Java luôn pass argument **by value**; reference value không phải object itself. Primitive/reference, scope, conversion và method-call rules là nền để hiểu các module Core phía sau.

Module này cố ý không đào sâu object design, inheritance, collection hay concurrency; nó chuẩn hóa language foundation trước.
