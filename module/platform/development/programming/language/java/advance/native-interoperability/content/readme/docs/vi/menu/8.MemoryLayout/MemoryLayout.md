<a id="back-to-top"></a>

# MemoryLayout và biểu diễn native data

## Menu
- [Vì sao native data cần MemoryLayout?](#memory-layout-purpose)
- [ValueLayout và primitive carrier](#value-layouts)
- [Struct layout](#struct-layout)
- [Sequence layout](#sequence-layout)
- [Union layout](#union-layout)
- [AddressLayout và pointer-shaped data](#address-layout)
- [Alignment, size và offset](#layout-alignment-offset)
- [Layout path và structured memory access](#layout-path-access)

## <a id="memory-layout-purpose">Vì sao native data cần MemoryLayout?</a>

<details>
<summary>Click for details</summary>

Native code nhìn dữ liệu như bytes tuân theo một binary layout cụ thể. Java object layout không phải contract tương đương với C struct, vì JVM có quyền tổ chức object theo cách riêng.

MemoryLayout cho phép Java mô tả **hình dạng dữ liệu trong memory**: kích thước, alignment, thứ tự field, sequence, union hoặc address. Layout sau đó được dùng để tính offset và truy cập MemorySegment có cấu trúc.

Ví dụ một C struct:

~~~c
struct Point {
    int x;
    int y;
};
~~~

có thể được biểu diễn bằng các ValueLayout nằm trong StructLayout. Điều quan trọng là Java đang mô tả binary contract của native data, không phải tạo một Java class “giống struct”.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="value-layouts">ValueLayout và primitive carrier</a>

<details>
<summary>Click for details</summary>

ValueLayout mô tả một giá trị scalar trong memory và carrier Java dùng để đọc/ghi giá trị đó. Các constant quen thuộc gồm ValueLayout.JAVA_BYTE, JAVA_INT, JAVA_LONG, JAVA_FLOAT và JAVA_DOUBLE.

~~~java
ValueLayout.OfInt intLayout = ValueLayout.JAVA_INT;

try (Arena arena = Arena.ofConfined()) {
    MemorySegment segment = arena.allocate(intLayout);
    segment.set(intLayout, 0, 123);
    int value = segment.get(intLayout, 0);
}
~~~

Carrier chỉ nói Java type dùng ở API boundary; nó không có nghĩa mọi C type cùng tên đều map trực tiếp sang Java primitive tương ứng. Mapping vẫn phải dựa trên ABI của native library.

ValueLayout còn mang byte order và alignment constraint. Đây là lý do dùng layout rõ ràng tốt hơn hard-code số byte mà không biết contract.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="struct-layout">Struct layout</a>

<details>
<summary>Click for details</summary>

StructLayout mô tả các member được đặt **nối tiếp nhau** trong memory. Khi native ABI yêu cầu padding giữa member, layout phải biểu diễn padding đó rõ ràng.

Ví dụ C:

~~~c
struct TaggedValue {
    char kind;
    int value;
};
~~~

Một layout Java 21 tương ứng với trường hợp có 3 byte padding:

~~~java
StructLayout taggedValue = MemoryLayout.structLayout(
        ValueLayout.JAVA_BYTE.withName("kind"),
        MemoryLayout.paddingLayout(3),
        ValueLayout.JAVA_INT.withName("value")
);
~~~

Không nên giả định MemoryLayout.structLayout sẽ “đoán ABI C” cho mình. Padding/alignment phải phản ánh native contract thật. Sai một offset có thể khiến Java đọc nhầm field hoặc truyền structure hỏng cho native function.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="sequence-layout">Sequence layout</a>

<details>
<summary>Click for details</summary>

SequenceLayout mô tả một dãy nhiều phần tử có cùng layout, tương tự cách nhìn binary của C array.

~~~java
SequenceLayout tenInts =
        MemoryLayout.sequenceLayout(10, ValueLayout.JAVA_INT);
~~~

Layout này không tự cấp phát memory; nó chỉ mô tả shape. Có thể dùng byteSize của layout để cấp phát một segment đủ lớn, hoặc dùng layout path để chọn element.

SequenceLayout đặc biệt hữu ích khi structure native chứa array cố định:

~~~text
Header
 + flags
 + values[16]
 + checksum
~~~

Khi đó array không nên được mô tả bằng 16 field rời rạc. Sequence giữ intent rõ và giúp tính offset theo index thay vì magic number.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="union-layout">Union layout</a>

<details>
<summary>Click for details</summary>

UnionLayout mô tả nhiều member dùng **cùng một starting offset**. Đây là semantics của C union: cùng vùng bytes có thể được diễn giải theo nhiều shape khác nhau.

~~~java
UnionLayout number = MemoryLayout.unionLayout(
        ValueLayout.JAVA_INT.withName("asInt"),
        ValueLayout.JAVA_FLOAT.withName("asFloat")
);
~~~

Khác StructLayout, các member union không nằm nối tiếp. Size của union đủ để chứa member lớn nhất, và việc chọn cách diễn giải đúng thuộc trách nhiệm của protocol/native contract.

Một mẫu phổ biến là tagged union: một field tag cho biết member nào đang có hiệu lực. Java code phải đọc tag trước rồi chọn cách diễn giải phù hợp; MemoryLayout không tự biết quy tắc ngữ nghĩa đó.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="address-layout">AddressLayout và pointer-shaped data</a>

<details>
<summary>Click for details</summary>

AddressLayout là ValueLayout đặc biệt biểu diễn một giá trị có hình dạng **địa chỉ native**. Khi đọc một address từ MemorySegment, carrier nhận được là một MemorySegment đại diện cho address đó thay vì Java long tùy tiện.

Trong foreign function signature, AddressLayout thường xuất hiện ở vị trí tương ứng với pointer parameter hoặc pointer return.

~~~java
FunctionDescriptor descriptor = FunctionDescriptor.of(
        ValueLayout.JAVA_INT,
        ValueLayout.ADDRESS
);
~~~

Address không tự mang đầy đủ thông tin về vùng nhớ đích. `AddressLayout` có thể gắn **target layout** để mô tả shape Java kỳ vọng ở địa chỉ đó, nhưng target layout vẫn không chứng minh rằng vùng nhớ thật sự hợp lệ, đủ lớn, còn sống hay do Java sở hữu. Một pointer native có thể chỉ cung cấp address mà không biết chắc size/lifetime của target region; để biến nó thành vùng có thể dereference an toàn có thể cần quy tắc rõ về size/lifetime và, trong một số trường hợp, restricted reinterpretation.

Không nên biến pointer thành số long rồi làm arithmetic nếu FFM API đã có abstraction phù hợp. Trong Java 21, một số thao tác target-layout/reinterpret là restricted chính vì mô tả sai spatial/temporal bounds có thể làm mất các bảo đảm memory-safety.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="layout-alignment-offset">Alignment, size và offset</a>

<details>
<summary>Click for details</summary>

Ba giá trị cần kiểm tra khi map native structure là **size, alignment và offset**.

- byteSize() cho biết layout chiếm bao nhiêu byte;
- byteAlignment() cho biết alignment constraint;
- byteOffset(path...) tính offset của nested layout theo layout path.

Ví dụ:

~~~java
long valueOffset = taggedValue.byteOffset(
        MemoryLayout.PathElement.groupElement("value"));
~~~

Alignment không phải decoration. Native ABI có thể yêu cầu một field bắt đầu ở địa chỉ chia hết cho một alignment nhất định. Nếu layout bỏ padding hoặc dùng alignment sai, binary view của Java và native code sẽ không còn giống nhau.

Khi review binding, nên so sánh size/offset với header/compiler information của native side thay vì chỉ nhìn code Java.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="layout-path-access">Layout path và structured memory access</a>

<details>
<summary>Click for details</summary>

Layout path là cách chọn một member nằm sâu trong layout có cấu trúc. PathElement có thể chọn group member theo tên hoặc chọn element trong sequence.

~~~java
MemoryLayout.PathElement valueField =
        MemoryLayout.PathElement.groupElement("value");

long offset = taggedValue.byteOffset(valueField);
VarHandle handle = taggedValue.varHandle(valueField);
~~~

Ngoài `byteOffset(...)` và `varHandle(...)`, cùng path model còn hỗ trợ `select(...)` để lấy nested layout và `sliceHandle(...)` để tạo handle sinh slice cho vùng được chọn.

Điểm mạnh là access logic được derive từ layout thay vì lặp lại magic offset. Khi structure thay đổi, code dựa trên path dễ kiểm tra hơn code rải số 4, 8, 16 ở nhiều nơi.

MemoryLayout có thể tạo VarHandle cho structured access, nhưng mechanics sâu của VarHandle thuộc Dynamic Runtime. Trong module này chỉ cần hiểu VarHandle là một access mechanism mà layout có thể cung cấp; trọng tâm vẫn là native data contract.

Layout làm rõ native data contract; foreign-function call cần cùng mức kỷ luật cho function signature. Chương tiếp theo nối layout với SymbolLookup, FunctionDescriptor và Linker để Java có thể tìm và mô tả một native entry point có thể gọi.

</details>

- [Quay lại đầu trang](#back-to-top)
