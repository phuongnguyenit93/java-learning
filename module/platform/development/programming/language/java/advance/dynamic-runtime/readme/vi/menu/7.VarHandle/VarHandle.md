<a id="back-to-top"></a>

# VarHandle và ngữ nghĩa truy cập biến

## Menu
- [VarHandle là gì và vì sao cần?](#var-handle-purpose)
- [Variable type và coordinate types](#variable-and-coordinate-types)
- [Tạo VarHandle cho field, array element và byte[]/ByteBuffer view](#var-handle-creation)
- [Các nhóm access mode](#var-handle-access-modes)
- [Access mode type và signature polymorphism](#var-handle-access-mode-type)
- [Atomic update và compare-and-set family](#var-handle-atomic-updates)
- [Ranh giới với Java Memory Model](#var-handle-memory-model-boundary)

## <a id="var-handle-purpose">VarHandle là gì và vì sao cần?</a>

<details>
<summary>Xem chi tiết</summary>

`VarHandle` là một **tham chiếu có kiểu mạnh được xác định động** (dynamically strongly typed reference) tới một variable hoặc một họ variable. Nó thống nhất nhiều dạng truy cập biến dưới cùng một abstraction và cho phép chọn access semantics rõ ràng.

Ví dụ variable có thể là:

- static field;
- instance field;
- array element;
- component nhìn qua `byte[]` hoặc `ByteBuffer` view;
- một số API foreign/off-heap sử dụng VarHandle như cơ chế truy cập phụ trợ.

VarHandle là handle bất biến và không bộc lộ mutable state cho người dùng.

Khác MethodHandle:

```text
MethodHandle
→ thao tác có thể thực thi

VarHandle
→ variable location/family
   + access-mode semantics
```

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="variable-and-coordinate-types">Variable type và coordinate types</a>

<details>
<summary>Xem chi tiết</summary>

Mỗi VarHandle có:

- **variable type** `T`: kiểu giá trị được đọc/ghi;
- **coordinate types**: các giá trị cần để định vị variable cụ thể.

Ví dụ instance field:

```text
class Counter { int value; }

VarHandle tới Counter.value

variable type:
int

coordinate types:
(Counter)
```

Array element:

```text
int[] array

variable type:
int

coordinate types:
(int[], int)
          ↑ index
```

Tư duy theo coordinates giúp hiểu signature của `get`, `set`, `compareAndSet` mà không học thuộc từng method.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="var-handle-creation">Tạo VarHandle cho field, array element và byte[]/ByteBuffer view</a>

<details>
<summary>Xem chi tiết</summary>

Instance/static field:

```java
MethodHandles.Lookup lookup = MethodHandles.lookup();

VarHandle value =
        lookup.findVarHandle(
                Counter.class,
                "value",
                int.class
        );
```

Nếu `Counter` có `static int globalValue`, static field dùng factory riêng:

```java
VarHandle globalValue =
        lookup.findStaticVarHandle(
                Counter.class,
                "globalValue",
                int.class
        );
```

`findVarHandle` dành cho instance field; `findStaticVarHandle` dành cho static field.

Array element:

```java
VarHandle element =
        MethodHandles.arrayElementVarHandle(
                int[].class
        );
```

View trên bytes:

```java
VarHandle ints =
        MethodHandles.byteArrayViewVarHandle(
                int[].class,
                ByteOrder.BIG_ENDIAN
        );
```

`byteBufferViewVarHandle` cung cấp tương tự cho `ByteBuffer`.

Những view này đưa byte order/alignment concerns vào bài toán. Native memory đầy đủ không thuộc module này; khi chuyển sang FFM, handoff sang Native Interoperability.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="var-handle-access-modes">Các nhóm access mode</a>

<details>
<summary>Xem chi tiết</summary>

VarHandle nhóm access theo semantics:

```text
plain
→ get / set

opaque
→ getOpaque / setOpaque

acquire / release
→ getAcquire / setRelease

volatile
→ getVolatile / setVolatile

atomic / update
→ compareAndSet
→ compareAndExchange...
→ getAndSet
→ getAndAdd...
```

Mode mạnh hơn không mặc định “tốt hơn”. Nó biểu diễn hợp đồng ordering/atomicity khác và có thể có chi phí khác.

Ở module này, mục tiêu là biết VarHandle cho phép **chọn hợp đồng truy cập** thay vì dùng một get/set duy nhất. Việc chứng minh memory ordering chính xác thuộc Concurrency.

Không phải VarHandle nào cũng hỗ trợ mọi access mode. Ví dụ:

- VarHandle tới `final` field không hỗ trợ write và atomic-update modes;
- numeric atomic update chỉ áp dụng cho các variable type số được API hỗ trợ;
- bitwise atomic update chỉ áp dụng cho các variable type mà API cho phép.

Gọi một access mode không được hỗ trợ sẽ ném `UnsupportedOperationException`. Có thể kiểm tra trước bằng `isAccessModeSupported`.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="var-handle-access-mode-type">Access mode type và signature polymorphism</a>

<details>
<summary>Xem chi tiết</summary>

Giống MethodHandle, access-mode methods của VarHandle là **signature-polymorphic**.

Type của một access mode được derive từ:

```text
coordinate types
+
variable type
+
access mode
```

Ví dụ với instance field `int value`:

```text
coordinates: (Counter)
variable:    int

GET
→ (Counter)int

SET
→ (Counter,int)void

COMPARE_AND_SET
→ (Counter,int,int)boolean
```

API hỗ trợ:

```java
MethodType getType =
        handle.accessModeType(
                VarHandle.AccessMode.GET
        );
```

Nếu call-site signature không tương thích, có thể gặp `WrongMethodTypeException`.

Mặc định, access-mode invocation của VarHandle dùng **invoke behavior**: runtime có thể áp dụng các conversion tương tự `MethodHandle.invoke`/`asType` khi symbolic descriptor chưa khớp chính xác nhưng vẫn có thể thích nghi với access-mode type.

```java
VarHandle exact = handle.withInvokeExactBehavior();
```

View này chuyển access-mode invocation sang **invoke-exact behavior**: call site phải khớp chính xác với `accessModeType`, nếu không sẽ ném `WrongMethodTypeException`. Có thể kiểm tra bằng `hasInvokeExactBehavior()` và quay lại behavior mặc định bằng `withInvokeBehavior()`.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="var-handle-atomic-updates">Atomic update và compare-and-set family</a>

<details>
<summary>Xem chi tiết</summary>

Compare-and-set family dùng expected value để cập nhật có điều kiện:

```java
boolean updated =
        (boolean) VALUE.compareAndSet(
                counter,
                expected,
                next
        );
```

Mental model:

```text
read witness/current value
→ witness khớp expected theo ngữ nghĩa của access mode?
    ├─ yes → write next + success
    └─ no  → không ghi + thất bại
```

Với reference và phần lớn primitive, mental model trên gần với phép so sánh `==`. Riêng `float`/`double`, numeric và atomic update modes so sánh **bitwise representation**, vì vậy NaN hoặc `-0.0`/`+0.0` có thể cho kết quả khác trực giác từ toán tử `==`.

`compareAndExchange` trả lại witness/current value thay vì boolean. Weak CAS variants có thể thất bại giả (spurious failure) ngay cả khi expected khớp, nên thường được dùng trong vòng lặp retry.

`getAndAdd`, `getAndSet` và các bitwise atomic update variants là những thao tác read-modify-write.

Đừng từ đây nhảy thẳng tới “lock-free luôn tốt hơn lock”. Tính đúng đắn, contention và progress guarantees thuộc curriculum Concurrency.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="var-handle-memory-model-boundary">Ranh giới với Java Memory Model</a>

<details>
<summary>Xem chi tiết</summary>

VarHandle định nghĩa access modes có memory-ordering effects khác nhau, nhưng module này chỉ thiết lập vocabulary và API boundary.

Ví dụ:

```text
plain
opaque
acquire/release
volatile
```

Để trả lời chính xác:

- read/write nào happens-before read/write nào;
- acquire/release tạo ordering ra sao;
- CAS loop có linearizable không;
- publication pattern có safe không;

cần dùng Java Memory Model và cách suy luận của Concurrency.

`VarHandle` cũng cung cấp các thao tác fence như `acquireFence`, `releaseFence`, `fullFence` và các biến thể tương ứng. Ở đây chỉ cần nhận diện chúng là primitive về ordering; cách đặt fence đúng và chứng minh quan hệ memory ordering thuộc Concurrency.

Ranh giới của module:

```text
Dynamic Runtime
→ shape của VarHandle + access modes + cơ chế invocation

Concurrency
→ hệ quả về tính đúng đắn của memory ordering
```

Đây là boundary có chủ đích để tránh dạy JMM nửa vời trong một chapter API.

</details>

- [Quay lại đầu trang](#back-to-top)
