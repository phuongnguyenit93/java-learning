# Encapsulation

## <a id="encapsulation-model">Encapsulation không chỉ là private field</a>
Encapsulation là kiểm soát access tới state/implementation để caller tương tác qua abstraction ổn định. Private field hữu ích nhưng class có getter/setter unrestricted vẫn có thể expose toàn bộ internal decision. Boundary thật sự là caller được biết và thay đổi điều gì.

## <a id="invariant-protection">Bảo vệ invariant</a>
Invariant là condition phải luôn đúng với object hợp lệ. Constructor/factory establish invariant và public method phải preserve nó. Thay vì expose raw mutation, hãy cung cấp operation đủ context để validate state transition hoàn chỉnh.

```java
account.withdraw(amount); // enforce amount > 0 và balance rule
```

## <a id="tell-dont-ask-boundary">Behavior-oriented API và data exposure</a>
“Tell, don't ask” là heuristic: yêu cầu object thực hiện behavior thay vì pull toàn bộ data ra ngoài rồi quyết định ở nơi khác. Nó không phải absolute rule; reporting/read model hợp lý khi expose data. Dùng nó để phát hiện domain rule đang leak qua boundary.
