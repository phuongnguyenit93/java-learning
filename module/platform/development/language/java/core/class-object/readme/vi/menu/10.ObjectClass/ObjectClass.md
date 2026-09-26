# java.lang.Object

Mọi class Java đều trực tiếp hoặc gián tiếp kế thừa từ `java.lang.Object`. Vì vậy một số hành vi cơ bản tồn tại trên mọi object, nhưng điều đó không có nghĩa default cách triển khai luôn phù hợp với domain.

## <a id="object-root-type">Object là Root Reference Type</a>

Một reference kiểu `Object` có thể trỏ tới object của bất kỳ class nào:

```java
Object value = new Profile("An");
```

Nhưng static type `Object` chỉ expose hợp đồng của `Object`; muốn dùng member riêng của `Profile` cần type information/cast phù hợp.

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

`Cloneable`/`Object.clone()` có ngữ nghĩa khó dùng an toàn cho deep object graph và constructor/invariant design. Thường ưu tiên:

- copy constructor;
- factory;
- explicit mapping.

`finalize()` không phải công cụ quản lý tài nguyên đáng tin cậy. Tài nguyên nên dùng `AutoCloseable`/`try-with-resources` hoặc cơ chế dọn dẹp phù hợp khác.

chương tiếp theo xem một loại class đặc biệt: `enum` — typed set các constant nhưng vẫn là object thực sự.
