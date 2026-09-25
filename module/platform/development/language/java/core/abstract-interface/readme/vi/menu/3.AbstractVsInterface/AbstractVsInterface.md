# Abstract Class và Interface

## <a id="abstract-vs-interface-state">Khác biệt về state và constructor</a>
Abstract class có thể own instance field và constructor để initialize shared state. Interface không có per-instance field/constructor; field của nó là constant. Nếu subclass cần common implementation state với invariant được kiểm soát, abstract class có thể phù hợp.

## <a id="abstract-vs-interface-inheritance">Single class inheritance và multiple interface inheritance</a>
Class chỉ extend một class nhưng implement nhiều interface. Interface vì vậy compose role linh hoạt qua các class hierarchy không liên quan, trong khi abstract class là structural commitment mạnh hơn.

## <a id="selection-guidance">Khi nào chọn abstract class hay interface</a>
Ưu tiên interface cho capability/role có nhiều implementation và consumer nên depend vào. Ưu tiên abstract class khi shared base implementation/state và controlled extension point thuộc model. Hai cái có thể dùng cùng nhau: abstract class implement interface và cung cấp partial implementation.
