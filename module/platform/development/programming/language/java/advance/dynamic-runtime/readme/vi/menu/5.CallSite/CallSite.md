<a id="back-to-top"></a>

# CallSite và target có thể thay đổi

## Menu
- [CallSite, target và dynamic invoker](#call-site-model)
- [Kiểu cố định của CallSite](#call-site-type-invariant)
- [ConstantCallSite, MutableCallSite và VolatileCallSite](#call-site-kinds)
- [Cập nhật target và visibility](#call-site-target-updates)
- [Linkage của CallSite và thay đổi target](#call-site-linkage-vs-target-change)

## <a id="call-site-model">CallSite, target và dynamic invoker</a>

<details>
<summary>Xem chi tiết</summary>

`CallSite` là một object giữ một `MethodHandle target`. Một `invokedynamic` instruction sau khi được link tới CallSite sẽ chuyển lời gọi tới **target hiện tại** của CallSite đó.

Ba thành phần:

```text
CallSite
├── type()          → MethodType cố định
├── getTarget()     → target hiện tại
└── dynamicInvoker()→ MethodHandle gọi xuyên qua CallSite
```

Ví dụ:

```java
MethodHandle target =
        MethodHandles.constant(
                String.class,
                "v1"
        );

CallSite site =
        new ConstantCallSite(target);

MethodHandle invoker =
        site.dynamicInvoker();
```

Điểm quan trọng: CallSite không tự “tìm method”. Nó là abstraction **giữ target** sau khi bootstrap hoặc runtime policy đã quyết định target nào phù hợp.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="call-site-type-invariant">Kiểu cố định của CallSite</a>

<details>
<summary>Xem chi tiết</summary>

Type của một CallSite được xác định khi tạo và không thay đổi:

```java
MethodType type = site.type();
```

Nếu target hiện tại là:

```text
(String)String
```

thì mọi target thay thế sau này cũng phải có **exactly cùng MethodType**.

```java
MutableCallSite site =
        new MutableCallSite(
                MethodType.methodType(
                        String.class,
                        String.class
                )
        );

site.setTarget(targetWithSameType);
```

Target sai type sẽ gây `WrongMethodTypeException`.

Mental model:

```text
CallSite identity
        +
permanent MethodType
        ↓
target có thể cố định hoặc thay đổi
        nhưng
target.type() luôn phải == site.type()
```

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="call-site-kinds">ConstantCallSite, MutableCallSite và VolatileCallSite</a>

<details>
<summary>Xem chi tiết</summary>

Ba cách triển khai chính:

| Type | Target | Visibility model |
| --- | --- | --- |
| `ConstantCallSite` | Không đổi | Target permanent |
| `MutableCallSite` | Có thể đổi | Update giống ordinary field; thread khác có thể chưa thấy ngay |
| `VolatileCallSite` | Có thể đổi | Update/read có semantics tương tự volatile |

`ConstantCallSite` phù hợp khi bootstrap chỉ cần resolve target một lần:

```java
return new ConstantCallSite(target);
```

`MutableCallSite` phù hợp với runtime optimization nơi target có thể được cập nhật và visibility được quản lý có chủ đích.

`VolatileCallSite` đơn giản hơn khi cần mọi thread quan sát target update theo volatile semantics, nhưng mức đồng bộ chặt hơn có thể làm chi phí thực thi cao hơn.

Không chọn subclass chỉ vì tên nghe phù hợp; hãy bắt đầu từ câu hỏi: **target có cần đổi không, và thread khác cần thấy update với đảm bảo nào?**

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="call-site-target-updates">Cập nhật target và visibility</a>

<details>
<summary>Xem chi tiết</summary>

Với `MutableCallSite`, `setTarget` không đảm bảo thread khác nhìn thấy target mới ngay theo cách volatile write làm.

```java
site.setTarget(newTarget);
```

Nếu cần buộc một tập mutable call sites được đồng bộ hóa tới các thread khác, API cung cấp:

```java
MutableCallSite.syncAll(
        new MutableCallSite[]{site}
);
```

Khi có nhiều writer, `setTarget` và `syncAll` thường cần được phối hợp bằng mutual exclusion để tránh race giữa các lần publish target. Reader có thể thấy target mới ngay sau `setTarget` trước cả `syncAll`, hoặc vẫn thấy target cũ cho tới khi `syncAll` hoàn tất. `syncAll` có thể tốn chi phí, nên phù hợp hơn với batch update không quá thường xuyên.

`VolatileCallSite` có semantics mạnh hơn:

```java
volatileSite.setTarget(newTarget);
```

Ở module này chỉ cần nắm hành vi cấp API. Việc chứng minh happens-before, memory ordering hay xây lock-free algorithm thuộc Java Concurrency.

Pitfall phổ biến là thấy “Mutable” rồi giả định update target giống volatile/reference atomic publication. Không nên suy luận như vậy.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="call-site-linkage-vs-target-change">Linkage của CallSite và thay đổi target</a>

<details>
<summary>Xem chi tiết</summary>

Hai hành vi phải tách biệt:

```text
invokedynamic linkage
→ instruction được resolve/link tới một CallSite
→ linkage này không liên tục đổi sang CallSite khác

CallSite target update
→ MutableCallSite / VolatileCallSite có thể đổi MethodHandle target
→ dynamic invoker sau đó dispatch tới target hiện tại
```

Vì vậy câu “invokedynamic relink mỗi lần target đổi” là mental model sai.

Đúng hơn:

```text
instruction
   │  link once
   ↓
CallSite
   │  target may change
   ↓
MethodHandle target
```

Phân biệt này rất quan trọng khi học bootstrap lifecycle ở chapter tiếp theo.

</details>

- [Quay lại đầu trang](#back-to-top)
