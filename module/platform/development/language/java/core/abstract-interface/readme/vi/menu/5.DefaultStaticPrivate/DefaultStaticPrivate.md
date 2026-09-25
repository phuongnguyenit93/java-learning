# Default, Static và Private Method trong Interface

## <a id="default-method">Default method</a>
Default method là inheritable instance implementation khai báo trong interface. Nó cho phép interface evolve bằng cách thêm behavior mà không bắt mọi existing implementation lập tức thêm body, đồng thời vẫn giữ virtual instance dispatch.

## <a id="static-interface-method">Static interface method</a>
Static interface method thuộc interface type và không inherit thành instance method của implementing class. Gọi qua interface name. Nó hữu ích cho factory, validator và utility gắn chặt với contract.

## <a id="private-interface-method">Private interface helper</a>
Private interface method cho phép default/static method share implementation detail mà không expose thêm contract member cho implementer. Nó chỉ là helper nội bộ, không override/call từ implementing class.

## <a id="default-method-evolution">Interface evolution compatibility</a>
Default method giúp library evolve interface mà vẫn giữ source/binary compatibility cho nhiều implementation cũ. Nhưng không phải mọi change đều safe: thêm abstract method, tạo default conflict hoặc đổi contract vẫn có thể break client.
