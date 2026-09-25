# Vòng đời tạo Object

## <a id="allocation-initialization-construction">Mental model allocation → initialization → construction</a>
Về semantics, object creation allocate storage, cấp default value cho field, chạy superclass construction, áp instance initializer rồi chạy constructor body trước khi trả reference. JVM có thể optimize allocation nhưng phải giữ Java-visible initialization semantics.

## <a id="constructor-dynamic-dispatch-risk">Rủi ro gọi overridable method trong constructor</a>
Instance method vẫn virtual dispatch trong construction. Nếu superclass constructor gọi overridable method, subclass override có thể chạy trước khi subclass field explicit initialize. Override khi đó có thể thấy default value và phá assumption.

```java
class Parent { Parent() { hook(); } void hook() {} }
class Child extends Parent { int x = 42; @Override void hook(){ /* x có thể là 0 */ } }
```

Nên tránh gọi overridable behavior từ constructor trừ khi contract cố ý thiết kế như vậy.

## <a id="this-escape">this escape trong construction</a>
`this` escape khi object chưa construct xong trở nên reachable ở nơi khác, ví dụ register listener, start thread hoặc lưu vào shared state. Code khác có thể thấy invariant chưa hoàn tất. Hãy giữ construction private tới khi initialization xong rồi mới publish object.
