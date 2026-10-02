# Generics

Module này xây mental model cho **parametric polymorphism và type safety ở compile time** trong Java. Generics cho phép một abstraction làm việc với nhiều type mà vẫn giữ contract kiểu rõ ràng thay vì đẩy kiểm tra type sang runtime.

## Learning flow

1. vì sao Generics tồn tại;
2. generic types và generic methods;
3. bounded type parameters;
4. invariance và subtyping;
5. wildcards và PECS;
6. raw types và legacy boundaries;
7. type erasure và runtime model;
8. limitations và generic API design.

## Mental model cần giữ

Generic type argument chủ yếu là compile-time contract. Vì type erasure và invariance, trực giác “`List<Dog>` là `List<Animal>`” không đúng. Wildcard mô tả capability của reference, còn type parameter mô tả quan hệ kiểu mà declaration cần duy trì.

Collection là nơi Generics được dùng rộng rãi, nhưng module này tập trung vào type-system semantics và API design.
