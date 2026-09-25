# Interface

## <a id="interface-contract">Interface như behavioral contract</a>
Interface đặt tên role/contract mà class, enum, record hoặc proxy có thể implement. Consumer phụ thuộc behavior mà không cần biết implementation class. Interface không đồng nghĩa stateless; nó chỉ không chứa per-instance field của riêng interface.

## <a id="interface-fields">Interface field là public static final</a>
Field khai báo trong interface implicit `public static final` và phải initialize. Nó là constant gắn với interface chứ không phải instance state. Mutable object phía sau final reference vẫn mutable nên tránh expose mutable global state qua interface field.

## <a id="interface-method-kinds">Các loại abstract/default/static/private method</a>
Interface có thể khai báo abstract instance method, default method, static method và private helper method. Abstract/default tham gia instance contract, static thuộc interface type, private chỉ phục vụ reuse nội bộ.

## <a id="interface-implementation">Implement nhiều contract</a>
Class có thể implement nhiều interface, giúp một object đóng nhiều role mà không cần multiple class inheritance. Default conflict phải resolve explicit; nhiều abstract contract không xung đột có thể implement cùng nhau.
