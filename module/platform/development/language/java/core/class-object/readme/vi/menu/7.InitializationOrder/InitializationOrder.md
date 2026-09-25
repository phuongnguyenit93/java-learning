# Thứ tự khởi tạo

## <a id="class-initialization-order">Static initialization order</a>
Trước static initializer, static field đã có default value. Explicit static field initializer và static block sau đó chạy theo textual order của class. Superclass initialization thường xảy ra trước subclass initialization khi subclass được active initialize.

## <a id="instance-initialization-order">Order của instance field/block/constructor</a>
Sau khi superclass constructor portion hoàn tất, subclass instance field và instance initializer block chạy theo textual order, rồi constructor body mới chạy. Default field value tồn tại trước explicit initializer.

## <a id="inheritance-initialization-order">Initialization order qua inheritance</a>
Với `new Child()`, chỉ có một object allocation nhưng construction đi qua superclass chain trước subclass initialization/body. Static initialization và instance construction là hai lifecycle khác nhau. Order này giải thích vì sao gọi overridable method trong constructor có thể thấy subclass field trước explicit initializer.
