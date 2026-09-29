# Object-Oriented Programming

## <a id="oop-what">1. OOP là gì?</a>

Object-Oriented Programming là paradigm tổ chức chương trình xoay quanh **object có identity, state, behavior và responsibility**, đồng thời để các object cộng tác thông qua contract rõ ràng.

OOP không đồng nghĩa với việc “chia code thành nhiều class”. Class chỉ là một cơ chế mà một số ngôn ngữ dùng để xây object model.

## <a id="oop-why">2. Tại sao OOP tồn tại?</a>

Khi dữ liệu và những rule thao tác lên dữ liệu bị tách rời khắp hệ thống, thay đổi một business rule có thể lan sang nhiều nơi.

OOP cố gắng đặt state và behavior liên quan về đúng responsibility boundary để giảm ảnh hưởng của thay đổi.

## <a id="oop-before">3. Cách đơn giản hơn là gì?</a>

Procedural programming có thể tổ chức chương trình quanh procedure và data structure riêng biệt. Cách này rất hiệu quả cho nhiều bài toán và không phải “thấp hơn” OOP.

OOP trở nên hữu ích khi domain có nhiều entity/collaborator với lifecycle, responsibility và behavior tương tác phức tạp.

## <a id="oop-solution">4. OOP giải quyết ra sao?</a>

```text
state + behavior
      ↓
responsible object
      ↓
collaboration through contracts
```

Các concept thường gặp gồm encapsulation, abstraction, composition, inheritance/subtyping và polymorphism.

## <a id="oop-boundary">5. Boundary với Java</a>

Module này chỉ sở hữu **OOP paradigm ở mức language-neutral**.

Keyword, access modifier, class/interface mechanics và Java-specific dispatch thuộc `programming/language/java/core/oop` và các Java Core module liên quan.
