# Polymorphism

## <a id="subtype-polymorphism">Subtype polymorphism</a>
Subtype polymorphism cho phép code phụ thuộc supertype nhưng làm việc với nhiều concrete implementation. Caller dùng common contract và không cần type-specific branch cho từng implementation.

## <a id="dynamic-dispatch">Runtime dynamic dispatch</a>
Với overridden instance method, Java chọn implementation từ runtime class của receiver object. Compiler đã chọn method signature dựa trên compile-time type; runtime dispatch quyết định override body nào chạy.

```java
Animal a = new Dog();
a.speak(); // Dog override
```

## <a id="substitutability">Substitutability và behavioral expectation</a>
Subtype nên thỏa promise có ý nghĩa của supertype: accepted state, postcondition, invariant và side-effect expectation. Type compatibility không tự guarantee substitutability tốt. Subclass buộc caller special-case nó là dấu hiệu polymorphic design yếu.
