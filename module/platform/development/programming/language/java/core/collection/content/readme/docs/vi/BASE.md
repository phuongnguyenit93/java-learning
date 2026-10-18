# Java Collections

Module này xây decision model cho **Collection Framework**: chọn abstraction và implementation dựa trên ordering, uniqueness, key/value lookup, queue semantics, mutation, equality/hash behavior và performance characteristic.

## Learning flow

1. collection hierarchy;
2. `List`;
3. `Set`;
4. `Map`;
5. `Queue` và `Deque`;
6. iteration;
7. ordering và sorting;
8. equality/hashing trong collection;
9. mutable và immutable collections;
10. fail-fast behavior;
11. chọn implementation phù hợp;
12. enum-based collections.

## Mental model cần giữ

Không học Collections như catalog class. Bắt đầu từ requirement của dữ liệu, chọn contract trước rồi mới chọn implementation. Generics cung cấp type-safety; Object Contract giải thích equality/hash/order; concurrency collections nằm ngoài phạm vi Java Core collection cơ bản.
