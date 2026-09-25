# Ép kiểu trong Java

## <a id="primitive-casting">Widening và narrowing primitive conversion</a>
Widening primitive conversion thường implicit. Narrowing cần explicit cast vì có thể mất thông tin do truncation, wraparound hoặc floating-to-integral conversion.

```java
int n = 100;
long wide = n;
byte narrow = (byte) 130; // -126
```

Cast không tự validate range; nếu correctness cần range check, hãy validate trước hoặc dùng exact conversion helper.

## <a id="reference-upcast-downcast">Reference upcast và downcast</a>
Upcast subtype reference sang supertype là type-safe và thường implicit. Downcast yêu cầu runtime kiểm tra object thực có compatible với subtype đích hay không. Cast chỉ đổi compile-time view của reference, không đổi runtime class của object.

## <a id="instanceof-safe-cast">instanceof và safe casting</a>
Dùng `instanceof` khi behavior thật sự phụ thuộc runtime type. Pattern matching có thể bind luôn variable đã narrow. Nếu code có quá nhiều type branch, nên xem lại liệu polymorphism có model behavior tốt hơn không.

## <a id="class-cast-failure">Boundary của ClassCastException</a>
Downcast hợp lệ về syntax vẫn có thể fail ở runtime. `ClassCastException` nghĩa là object thực không phải instance của target type. Ưu tiên contract mạnh hơn, generics, polymorphism hoặc compatibility check thay vì dùng exception như cách bình thường để dò type.
