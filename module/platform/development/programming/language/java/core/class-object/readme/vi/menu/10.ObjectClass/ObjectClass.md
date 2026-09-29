# java.lang.Object

`java.lang.Object` là class gốc của hệ phân cấp class thông thường trong Java. Mọi class khác `Object` đều có `Object` ở đâu đó trong chuỗi superclass, nhờ đó các instance của class có một hợp đồng nền chung. Tuy nhiên cách triển khai mặc định không phải lúc nào cũng phù hợp với ngữ nghĩa của miền nghiệp vụ.

## <a id="object-root-type">Object là Root Reference Type</a>

Một reference kiểu `Object` có thể trỏ tới object của bất kỳ class nào:

```java
Object value = new BankAccount("A-01");
```

Nhưng kiểu tĩnh (static type) `Object` chỉ cho phép truy cập hợp đồng của `Object`; muốn dùng member riêng của `BankAccount` cần thông tin kiểu và phép cast phù hợp.

Primitive không phải subtype của `Object`; boxing wrapper giúp primitive tham gia API yêu cầu reference type.

## <a id="object-core-methods">Các Method cốt lõi của Object</a>

Những method thường gặp:

- `getClass()` — runtime class;
- `toString()` — text cách biểu diễn;
- `equals()` — logical equality hook;
- `hashCode()` — hash hợp đồng;
- `wait/notify/notifyAll` — monitor coordination;
- `clone()` — legacy copying mechanism;
- `finalize()` — legacy finalization mechanism đã bị deprecate/loại bỏ dần.

`equals`, `hashCode`, `toString` có hợp đồng riêng và được học sâu hơn trong module `object-contract`.

## <a id="clone-finalize-boundary">clone và finalize</a>

`Cloneable`/`Object.clone()` có ngữ nghĩa khó dùng an toàn cho object graph sâu cũng như thiết kế constructor/invariant. Thường ưu tiên:

- copy constructor;
- factory;
- explicit mapping.

`finalize()` không phải công cụ quản lý tài nguyên đáng tin cậy. Tài nguyên nên dùng `AutoCloseable`/`try-with-resources` hoặc cơ chế dọn dẹp phù hợp khác.

Chương tiếp theo xem một loại class đặc biệt: `enum` — một tập constant có kiểu rõ ràng nhưng mỗi constant vẫn là object thực sự.
