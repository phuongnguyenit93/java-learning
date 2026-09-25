# Object Aliasing và Mutability

## <a id="aliasing-model">Nhiều reference cùng tới một mutable object</a>
Aliasing xảy ra khi nhiều reference nhận diện cùng object. Với immutable object thường không vấn đề; với mutable object, mutation qua một alias sẽ visible qua alias khác. Cần suy luận bằng identity và ownership, không chỉ bằng variable name.

```java
List<String> a = new ArrayList<>();
List<String> b = a;
b.add("x"); // a cũng thấy "x"
```

## <a id="shared-mutable-state">Hệ quả shared mutable state</a>
Shared mutable state tăng coupling vì caller có thể thấy change do nơi khác tạo ra. Nó làm invariant, testing, caching, concurrency và reasoning về ownership khó hơn. Encapsulation, immutability, ownership rule và copy giúp giảm uncertainty.

## <a id="aliasing-in-collections">Aliasing qua collection và returned reference</a>
Trả internal mutable collection, lưu trực tiếp mutable object của caller hoặc expose array có thể leak alias qua API boundary. Unmodifiable wrapper chỉ chặn mutation qua wrapper nhưng vẫn có thể reflect source mutation; defensive copy thay đổi ownership semantics.
