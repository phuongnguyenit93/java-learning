# Constructor

## <a id="constructor-purpose">Mục đích constructor và invariant</a>
Constructor initialize object vừa được allocate và nên thiết lập invariant trước khi reference escape. Constructor không phải ordinary method: không có return type, dùng class name và tham gia initialization/chaining process đặc biệt.

## <a id="constructor-overloading">Constructor overloading và chaining</a>
Class có thể overload constructor. Một constructor delegate sang constructor khác bằng `this(...)`, còn `super(...)` gọi superclass constructor. Constructor invocation theo syntax truyền thống phải đứng đầu, giúp object đi qua một initialization chain xác định.

## <a id="default-constructor">Quy tắc default constructor</a>
Nếu class không khai báo constructor, compiler có thể sinh no-arg default constructor với accessibility theo class và gọi accessible superclass no-arg constructor. Khi đã khai báo bất kỳ constructor nào, implicit default constructor không còn được sinh.

## <a id="constructor-exceptions">Constructor fail và partially-created state boundary</a>
Nếu constructor throw, expression không trả về object reference đã construct thành công. Tuy vậy side effect trước failure vẫn có thể tồn tại, và `this` có thể bị quan sát nếu đã escape. Tránh publish `this` hoặc register callback trước khi invariant hoàn tất.
