<a id="back-to-top"></a>

# Lookup và quyền truy cập

## Menu
- [Lookup là gì và vì sao cần?](#lookup-purpose)
- [Lookup class và lookup modes](#lookup-class-and-modes)
- [lookup, publicLookup, Lookup.in và privateLookupIn](#lookup-context)
- [Tạo MethodHandle cho method, constructor và field accessor](#member-handle-lookup)
- [Các lỗi lookup và access checking](#lookup-access-failures)

## <a id="lookup-purpose">Lookup là gì và vì sao cần?</a>

<details>
<summary>Xem chi tiết</summary>

`MethodHandles.Lookup` là factory dùng để tạo `MethodHandle` hoặc `VarHandle` khi việc tạo đó cần access checking. Điểm quan trọng: **quyền được kiểm tra lúc handle được tạo, không phải mỗi lần handle được invoke**.

```java
MethodHandles.Lookup lookup = MethodHandles.lookup();
```

`lookup` giữ một lookup class và tập lookup modes. Vì vậy có thể xem nó như một object mang quyền: code nào giữ lookup nào thì chỉ có thể tạo những handle tương ứng với quyền của lookup đó.

Mental model:

```text
class của bên gọi / lookup class
        +
lookup modes
        ↓
quyền của Lookup
        ↓
findVirtual / findStatic / findConstructor / findVarHandle ...
        ↓
access check xảy ra tại đây
        ↓
handle đã tạo có thể invoke mà không re-check member access mỗi lần
```

Đây là lý do không nên coi `Lookup` chỉ là “helper class để tìm method”.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="lookup-class-and-modes">Lookup class và lookup modes</a>

<details>
<summary>Xem chi tiết</summary>

`lookupClass()` cho biết class đóng vai trò access context. `lookupModes()` mô tả những mode còn được phép. Java 21 có các mode `PUBLIC`, `PRIVATE`, `PROTECTED`, `PACKAGE`, `MODULE`, `UNCONDITIONAL` và `ORIGINAL`.

Hai mode ít gặp nhưng cần nhận diện:

- `ORIGINAL`: cho biết lookup bắt nguồn từ original lookup class (ví dụ `MethodHandles.lookup()` hoặc lookup do VM cấp cho bootstrap); mode này bị mất khi lookup được transform;
- `UNCONDITIONAL`: gắn với `publicLookup()` và cho phép public access tới các package được export vô điều kiện, không dựa trên readability của một module bên gọi cụ thể.

```java
MethodHandles.Lookup lookup = MethodHandles.lookup();

System.out.println(lookup.lookupClass());
System.out.println(lookup.lookupModes());
```

Quyền truy cập có thể **mất bớt** khi lookup được chuyển sang context khác. Vì vậy đừng chỉ hỏi “class đích có private không?”, mà cần hỏi:

```text
lookup được tạo ở đâu?
→ lookup class là ai?
→ lookup còn mode nào?
→ module/package relationship ra sao?
```

Module boundary rất quan trọng từ Java 9 trở đi. Một member có thể public nhưng package/module vẫn không open/readable theo cách lookup cần.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="lookup-context">lookup, publicLookup, Lookup.in và privateLookupIn</a>

<details>
<summary>Xem chi tiết</summary>

Các thao tác factory/context sau có vai trò khác nhau:

- `MethodHandles.lookup()`: lookup gắn với class của bên gọi;
- `MethodHandles.publicLookup()`: lookup hạn chế cho public access;
- `lookup.in(Target.class)`: chuyển lookup class sang target nhưng quyền chỉ có thể giữ nguyên hoặc giảm;
- `MethodHandles.privateLookupIn(Target.class, lookup)`: tạo lookup có private access vào target khi lookup đầu vào có đủ `MODULE` + `PRIVATE` và quan hệ module/package cho phép.

Ví dụ:

```java
MethodHandles.Lookup callerLookup = MethodHandles.lookup();

MethodHandles.Lookup targetLookup =
        MethodHandles.privateLookupIn(
                Target.class,
                callerLookup
        );
```

`privateLookupIn` không phải “bypass private”. Với target ở module khác, module của lookup phải đọc module target và package chứa target phải được `opens` phù hợp; lookup kết quả dùng target làm lookup class, giữ class nguồn làm `previousLookupClass()` và mất `MODULE` access khi chuyển qua module.

Khi framework phải truy cập member không public, đây là nơi nên phân tích quyền trước khi nghĩ tới giải pháp vòng bằng JVM flags hoặc mở module quá rộng.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="member-handle-lookup">Tạo MethodHandle cho method, constructor và field accessor</a>

<details>
<summary>Xem chi tiết</summary>

`Lookup` cung cấp nhiều factory cho executable handles:

```java
MethodHandle virtual =
        lookup.findVirtual(
                Greeter.class,
                "greet",
                MethodType.methodType(
                        String.class,
                        String.class
                )
        );

MethodHandle constructor =
        lookup.findConstructor(
                Greeter.class,
                MethodType.methodType(void.class)
        );
```

Với `findConstructor`, `MethodType` đầu vào dùng return type `void` để mô tả descriptor của constructor, nhưng handle được trả về có return type là chính class được khởi tạo. Ví dụ trên tạo handle kiểu `()Greeter`.

Field cũng có thể được biểu diễn bằng **MethodHandle accessor**:

```java
MethodHandle getter =
        lookup.findGetter(
                User.class,
                "name",
                String.class
        );
```

Nhưng khi mục tiêu là **variable access semantics** như volatile/acquire/release/CAS, abstraction phù hợp là `VarHandle`, không phải getter/setter MethodHandle.

```text
field as callable getter/setter
→ MethodHandle accessor

field as variable with memory/access modes
→ VarHandle
```

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="lookup-access-failures">Các lỗi lookup và access checking</a>

<details>
<summary>Xem chi tiết</summary>

Hai nhóm lỗi phổ biến:

```text
symbolic member không tồn tại / sai descriptor
→ NoSuchMethodException
→ NoSuchFieldException

member tồn tại nhưng Lookup không có quyền
→ IllegalAccessException
```

Ví dụ một lỗi dễ gặp:

```java
lookup.findVirtual(
        Greeter.class,
        "greet",
        MethodType.methodType(
                String.class,
                Object.class // sai parameter type
        )
);
```

MethodHandle lookup yêu cầu symbolic signature chính xác của member đích; nó không tự “search overload gần nhất”.

Khi debug access:

1. kiểm tra lookup class/modes;
2. kiểm tra visibility của declaring class và member;
3. kiểm tra package/module readability/openness;
4. kiểm tra chính xác `MethodType`;
5. chỉ sau đó mới xem adaptation của handle.

</details>

- [Quay lại đầu trang](#back-to-top)
