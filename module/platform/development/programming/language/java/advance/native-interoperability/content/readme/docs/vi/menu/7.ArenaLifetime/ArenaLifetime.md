<a id="back-to-top"></a>

# Arena, ownership và lifetime

## Menu
- [Arena dùng để làm gì?](#arena-purpose)
- [Arena và lifetime của native memory](#arena-lifetime)
- [Global, automatic, confined và shared arena](#arena-kinds)
- [Allocation và timely deallocation](#arena-allocation)
- [Temporal safety và segment validity](#temporal-safety)
- [Thread access và confinement](#arena-thread-access)

## <a id="arena-purpose">Arena dùng để làm gì?</a>

<details>
<summary>Click for details</summary>

Arena là abstraction của FFM dùng để **quản lý lifetime của native memory** và quy định segment nào được truy cập từ những luồng nào. Arena đồng thời là SegmentAllocator nên có thể cấp phát native segment trực tiếp.

Thay vì mỗi allocation có một lệnh free riêng rẽ, nhiều segment có thể cùng thuộc một arena. Khi closeable arena đóng, toàn bộ native memory do arena đó quản lý được giải phóng và các segment liên quan trở nên không còn hợp lệ.

Mental model nên là:

~~~text
Arena
 ├─ segment A
 ├─ segment B
 └─ segment C

close Arena
→ lifetime của A/B/C kết thúc cùng boundary
~~~

Cách tổ chức này giúp ownership trở nên explicit hơn raw pointer, đặc biệt khi một tác vụ native cần nhiều allocation có cùng vòng đời.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="arena-lifetime">Arena và lifetime của native memory</a>

<details>
<summary>Click for details</summary>

Lifetime của native memory không được GC quản lý theo cùng cách với Java object. Nếu vùng nhớ bị giải phóng quá sớm, pointer trở thành dangling; nếu không bao giờ giải phóng, process bị leak native memory.

Arena đưa lifetime đó thành một boundary có thể nhìn thấy trong code:

~~~java
MemorySegment segment;

try (Arena arena = Arena.ofConfined()) {
    segment = arena.allocate(128);
    // segment hợp lệ trong lifetime của arena
}

// segment không còn hợp lệ ở đây
~~~

FFM kiểm tra temporal validity trước khi access segment. Vì vậy use-after-close thường trở thành IllegalStateException thay vì âm thầm dereference vùng nhớ đã free.

Thiết kế tốt là đặt arena gần vòng đời của công việc: một request, một thao tác native hoặc một component có điểm kết thúc rõ ràng.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="arena-kinds">Global, automatic, confined và shared arena</a>

<details>
<summary>Click for details</summary>

Java 21 cung cấp bốn kiểu arena với trade-off lifetime/thread khác nhau.

| Arena | Lifetime | Close thủ công | Truy cập nhiều luồng |
| --- | --- | --- | --- |
| Arena.global() | không giới hạn | không | có |
| Arena.ofAuto() | GC quản lý | không | có |
| Arena.ofConfined() | có giới hạn | có | không |
| Arena.ofShared() | có giới hạn | có | có |

**Global arena** phù hợp với tài nguyên thực sự sống suốt process, nhưng rất dễ biến thành leak nếu dùng chỉ vì tiện.

**Automatic arena** để runtime quyết định thời điểm giải phóng sau khi arena/segment không còn reachable; nó không phù hợp khi cần giải phóng đúng thời điểm xác định.

**Confined arena** là lựa chọn tốt mặc định cho tác vụ một luồng vì ownership và thời điểm close rõ. **Shared arena** dùng khi nhiều luồng thực sự cần truy cập cùng segment và ứng dụng đã có chiến lược đồng bộ hóa.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="arena-allocation">Allocation và timely deallocation</a>

<details>
<summary>Click for details</summary>

Allocation native nên đi cùng kế hoạch giải phóng ngay tại nơi tạo tài nguyên.

~~~java
try (Arena arena = Arena.ofConfined()) {
    MemorySegment input = arena.allocate(256);
    MemorySegment output = arena.allocate(256);

    // gọi native function với input/output
} // cả hai allocation được giải phóng tại đây
~~~

Đây là **timely deallocation**: tài nguyên được trả lại khi công việc kết thúc, không chờ GC quyết định thời điểm.

Khi một API trả về MemorySegment cho bên gọi, cần tránh làm quyền sở hữu mơ hồ. Nếu segment phụ thuộc arena của callee nhưng bên gọi dùng sau khi method return/arena close thì thiết kế đó sai vòng đời. Một lựa chọn tốt hơn là bên gọi sở hữu arena rồi truyền allocator xuống, hoặc API giữ arena sống đúng bằng vòng đời của object trả về.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="temporal-safety">Temporal safety và segment validity</a>

<details>
<summary>Click for details</summary>

Temporal safety trả lời câu hỏi: **segment còn sống tại thời điểm access hay không?**

Với closeable arena, mọi segment thuộc arena chỉ hợp lệ cho tới khi arena đóng. Sau đó các thao tác đọc/ghi sẽ thất bại:

~~~java
Arena arena = Arena.ofConfined();
MemorySegment segment = arena.allocate(ValueLayout.JAVA_LONG);

arena.close();
segment.get(ValueLayout.JAVA_LONG, 0); // IllegalStateException
~~~

Điều này khác spatial safety. Spatial safety kiểm tra offset/size; temporal safety kiểm tra vòng đời. Một access có thể nằm đúng offset nhưng vẫn sai vì tài nguyên đã hết hạn.

Native code bên ngoài FFM vẫn có thể giữ raw address lâu hơn lifetime Java mong đợi. Vì vậy khi truyền pointer cho native library để lưu dùng về sau, phải mở rộng lifetime của backing arena tương ứng hoặc thiết kế protocol unregister/giải phóng rõ ràng.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="arena-thread-access">Thread access và confinement</a>

<details>
<summary>Click for details</summary>

Arena còn xác định **chính sách truy cập theo luồng**.

Segment từ `Arena.ofConfined()` chỉ được truy cập bởi owner thread. Chính thao tác `close()` trên confined Arena cũng bị ràng buộc bởi owner thread; close từ luồng khác thất bại với `WrongThreadException`. Truy cập segment từ luồng khác cũng thất bại thay vì âm thầm tạo data race:

~~~java
try (Arena arena = Arena.ofConfined()) {
    MemorySegment segment = arena.allocate(8);
    // chỉ owner thread nên access segment này
}
~~~

Khi thật sự cần chia sẻ segment, Arena.ofShared() cho phép nhiều luồng truy cập. Nhưng shared arena chỉ mở quyền truy cập; nó **không tự đồng bộ dữ liệu**. Nếu hai luồng cùng ghi cùng vùng nhớ, ứng dụng vẫn cần protocol đồng bộ phù hợp.

Vì thế chọn shared arena không nên là cách né WrongThreadException. Hãy bắt đầu từ câu hỏi về quyền sở hữu: dữ liệu có thật sự cần chia sẻ không, và ai phối hợp việc close khi các luồng vẫn còn dùng tài nguyên?

Khi một vùng memory đã có bounds, lifetime và thread accessibility rõ ràng, câu hỏi tiếp theo là các byte trong vùng đó mang ý nghĩa gì. Chương kế tiếp dùng MemoryLayout để mô tả size, alignment, field placement và pointer-shaped data.

</details>

- [Quay lại đầu trang](#back-to-top)
