# Nested và Inner Class

Đặt một type bên trong type khác có thể thể hiện rằng chúng liên quan chặt về mặt tổ chức hoặc cần chia sẻ ngữ cảnh. Nhưng các dạng nested class khác nhau có ngữ nghĩa rất khác nhau.

## <a id="static-nested-class">Static Nested Class</a>

Static nested class được khai báo với `static` và **không tự giữ reference tới outer instance**.

```java
class Profile {
    static class Builder { ... }
}
```

Nó giống một class bình thường về instance ngữ nghĩa, chỉ được đặt trong namespace của outer class. Phù hợp cho helper/type phụ có quan hệ logic mạnh với outer type nhưng không cần trạng thái của một outer object cụ thể.

## <a id="inner-class">Inner Class</a>

Non-static nested class là inner class và gắn với một outer instance:

```java
class Order {
    class LineView { ... }
}
```

Mỗi `LineView` được tạo thông qua một `Order` cụ thể và có thể truy cập instance member của outer object.

Điều này tiện nhưng cũng tạo coupling/lifetime relationship: giữ inner object có thể đồng thời giữ outer object reachable.

## <a id="local-anonymous-class">Local và Anonymous Class</a>

Local class được khai báo trong block/method. Anonymous class tạo một cách triển khai/class instance ngay tại biểu thức mà không đặt tên type riêng.

Chúng hữu ích cho hành vi cục bộ, nhưng lambda thường đơn giản hơn nếu chỉ cần implement functional interface.

Anonymous class vẫn là object/class với `this` riêng; lambda có ngữ nghĩa `this` khác và thuộc module functional/lambda.

## <a id="capture-semantics">Captured Local Variable</a>

Local/anonymous/inner-related mã có thể capture local variable chỉ khi variable là `final` hoặc **effectively final**.

```java
int limit = 10;
Runnable r = new Runnable() {
    public void run() {
        System.out.println(limit);
    }
};
```

Local variable được capture theo value ngữ nghĩa phù hợp với language model; không phải một mutable local slot được chia sẻ tùy ý.

chương tiếp theo nhìn vào superclass chung của mọi reference type: `java.lang.Object`.
