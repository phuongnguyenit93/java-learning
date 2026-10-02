<a id="back-to-top"></a>

# MemorySegment

## Menu
- [MemorySegment biểu diễn điều gì?](#memory-segment-model)
- [Heap segment và native segment](#heap-native-segments)
- [Mapped segment và file-backed native memory](#mapped-segments)
- [Spatial bounds và phạm vi truy cập](#segment-spatial-bounds)
- [Đọc và ghi dữ liệu qua MemorySegment](#segment-access)
- [Slice và view trên cùng vùng nhớ](#segment-slicing)

## <a id="memory-segment-model">MemorySegment biểu diễn điều gì?</a>

<details>
<summary>Click for details</summary>

MemorySegment là abstraction của FFM để biểu diễn **một vùng nhớ liên tục có biên rõ ràng**. Thay vì chỉ giữ một địa chỉ như raw pointer, segment biết kích thước vùng mà nó đại diện, trạng thái lifetime và chính sách truy cập theo luồng.

Trong Java 21 có hai nhóm chính:

- **heap segment**: vùng nhớ nằm trong Java heap, ví dụ segment tạo từ Java array;
- **native segment**: vùng nhớ ngoài Java heap, bao gồm native allocation và mapped segment.

Mapped segment không phải “loại thứ ba” độc lập; nó là native segment được backing bởi file mapping.

Điểm quan trọng là MemorySegment gom ba câu hỏi vốn tách rời ở raw pointer thành một model: vùng nhớ bắt đầu ở đâu, rộng bao nhiêu và còn hợp lệ hay không.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="heap-native-segments">Heap segment và native segment</a>

<details>
<summary>Click for details</summary>

Heap segment giúp dùng cùng API truy cập MemorySegment với dữ liệu vốn nằm trong Java heap:

~~~java
int[] values = {10, 20, 30};
MemorySegment heap = MemorySegment.ofArray(values);
~~~

Native segment thường được cấp phát qua Arena:

~~~java
try (Arena arena = Arena.ofConfined()) {
    MemorySegment nativeSegment = arena.allocate(16);
}
~~~

Hai segment đều có size và access API, nhưng ownership khác nhau. Heap memory vẫn chịu quản lý của JVM/GC. Native memory nằm ngoài heap và lifetime phụ thuộc arena hoặc nguồn tạo segment.

Khi truyền dữ liệu cho native function, native segment thường phù hợp hơn vì nó có địa chỉ native có thể dùng ở ABI boundary. Heap segment không nên được suy diễn thành raw native pointer tùy ý.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="mapped-segments">Mapped segment và file-backed native memory</a>

<details>
<summary>Click for details</summary>

Mapped segment ánh xạ một vùng file vào address space của process và biểu diễn vùng đó bằng MemorySegment. Trong Java 21, FileChannel có preview overload nhận Arena:

~~~java
try (FileChannel channel = FileChannel.open(
        path, StandardOpenOption.READ, StandardOpenOption.WRITE);
     Arena arena = Arena.ofConfined()) {

    MemorySegment mapped = channel.map(
            FileChannel.MapMode.READ_WRITE,
            0,
            channel.size(),
            arena);
}
~~~

Lifetime của mapping gắn với arena được truyền vào; khi closeable arena đóng, mapping được unmap theo contract của API.

Mapped segment còn cung cấp các thao tác liên quan đến mapping như `load()`, `isLoaded()`, `unload()` và `force()` khi nền tảng hỗ trợ. Chúng phục vụ việc nạp trước trang nhớ, quan sát trạng thái nạp, yêu cầu unload hoặc đẩy thay đổi xuống file; các thao tác này không thay thế contract về bounds/lifetime của segment.

Mapped memory vẫn chịu ảnh hưởng của file và hệ điều hành. Nội dung có thể thay đổi do process khác ghi file, và thao tác vượt ngoài các giả định của mapping có thể dẫn tới lỗi. Vì thế mapped segment cung cấp Java access model tốt hơn raw mapping nhưng không loại bỏ các vấn đề về concurrency/file system.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="segment-spatial-bounds">Spatial bounds và phạm vi truy cập</a>

<details>
<summary>Click for details</summary>

**Spatial bounds** là giới hạn theo không gian của segment: mọi lần đọc/ghi phải nằm hoàn toàn trong [0, byteSize). MemorySegment kiểm tra biên này trước khi thực hiện access.

Ví dụ segment 8 byte không cho phép đọc một int bắt đầu tại offset 6 vì 4 byte dữ liệu sẽ vượt ra ngoài vùng đại diện. Dùng `JAVA_INT_UNALIGNED` ở ví dụ này để tách riêng lỗi bounds khỏi lỗi alignment:

~~~java
try (Arena arena = Arena.ofConfined()) {
    MemorySegment segment = arena.allocate(8, ValueLayout.JAVA_INT.byteAlignment());
    segment.set(ValueLayout.JAVA_INT, 0, 100);             // hợp lệ
    segment.get(ValueLayout.JAVA_INT_UNALIGNED, 6);        // chỉ còn lỗi vượt biên
}
~~~

Ngoài size, layout access còn có alignment constraint. Do đó “offset nằm trong segment” chưa chắc đã đủ nếu offset không thỏa alignment của ValueLayout đang dùng.

Spatial safety giúp biến nhiều lỗi pointer arithmetic thành lỗi Java có thể quan sát thay vì memory corruption ngay lập tức.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="segment-access">Đọc và ghi dữ liệu qua MemorySegment</a>

<details>
<summary>Click for details</summary>

MemorySegment cung cấp get/set dựa trên ValueLayout. Layout cho biết kiểu carrier Java, kích thước, byte order và alignment mà access mong đợi.

~~~java
try (Arena arena = Arena.ofConfined()) {
    MemorySegment segment = arena.allocate(ValueLayout.JAVA_INT);

    segment.set(ValueLayout.JAVA_INT, 0, 42);
    int value = segment.get(ValueLayout.JAVA_INT, 0);

    System.out.println(value);
}
~~~

Cách này tốt hơn tự tính pointer rồi cast vì contract truy cập được biểu diễn bằng Java object. Tuy nhiên lập trình viên vẫn phải chọn đúng layout tương ứng với dữ liệu native thật.

Với dãy giá trị đồng nhất, các method dạng index như `getAtIndex`/`setAtIndex` biểu diễn vị trí theo **chỉ số phần tử** thay vì raw byte offset:

~~~java
try (Arena arena = Arena.ofConfined()) {
    MemorySegment values = arena.allocate(
            ValueLayout.JAVA_INT.byteSize() * 10,
            ValueLayout.JAVA_INT.byteAlignment());

    values.setAtIndex(ValueLayout.JAVA_INT, 3, 30);
    int value = values.getAtIndex(ValueLayout.JAVA_INT, 3);
}
~~~

Với chuỗi hoặc structure phức tạp, nên kết hợp MemorySegment với MemoryLayout thay vì rải các magic offset khắp code.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="segment-slicing">Slice và view trên cùng vùng nhớ</a>

<details>
<summary>Click for details</summary>

Slice là một view mới trên **cùng backing memory**, thường bắt đầu tại offset và có size nhỏ hơn. Nó không copy dữ liệu.

~~~java
try (Arena arena = Arena.ofConfined()) {
    MemorySegment whole = arena.allocate(16, ValueLayout.JAVA_INT.byteAlignment());
    MemorySegment firstInt = whole.asSlice(0, Integer.BYTES);

    firstInt.set(ValueLayout.JAVA_INT, 0, 7);
    int sameValue = whole.get(ValueLayout.JAVA_INT, 0);
}
~~~

Vì share backing memory nên thay đổi qua slice được nhìn thấy từ segment gốc và ngược lại. Slice cũng không tạo lifetime độc lập: nếu arena của backing memory đóng, mọi view liên quan đều mất hiệu lực.

Slice hữu ích để tách một buffer lớn thành các vùng logic mà vẫn giữ boundary check cho từng vùng. Khi thiết kế parser/native structure, điều này giúp code rõ hơn so với tự cộng offset ở mọi nơi.

Bounds trả lời segment được phép access **ở đâu**, nhưng chưa trả lời access đó hợp lệ **đến khi nào**. Chương tiếp theo đưa vào Arena như owner của native-allocation lifetime và thread-access policy.

</details>

- [Quay lại đầu trang](#back-to-top)
