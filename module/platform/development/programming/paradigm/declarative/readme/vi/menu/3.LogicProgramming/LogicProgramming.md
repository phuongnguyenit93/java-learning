<a id="back-to-top"></a>

# Mô hình dữ kiện logic, quan hệ và suy luận

## Menu
- [Lập trình logic như cách mô tả quan hệ](#declarative-logic-model)
- [Dữ kiện logic (fact) và các quan hệ đã biết](#declarative-facts-relations)
- [Quy tắc suy ra và các điều kiện logic](#declarative-inference-rules)
- [Mục tiêu truy vấn và kết luận phù hợp](#declarative-goal-queries)
- [Đặc tả quan hệ so với thuật toán tìm kiếm](#declarative-search-boundary)
- [Giới hạn giữa ý tưởng logic và cú pháp/trình giải Prolog](#declarative-prolog-boundary)

## <a id="declarative-logic-model">Lập trình logic như cách mô tả quan hệ</a>

<details>
<summary>Xem chi tiết</summary>

Lập trình logic mô tả **các quan hệ đúng** và quy tắc từ đó có thể suy ra quan hệ khác. Thay vì viết vòng lặp đi tìm từng người đủ điều kiện, ta diễn đạt mối liên hệ và đặt mục tiêu cần chứng minh.

Đây là một họ cách biểu diễn khai báo; từng hệ thống logic có quy tắc suy diễn và ngữ nghĩa riêng. Đừng đồng nhất ý tưởng khai báo quan hệ với cú pháp của một ngôn ngữ cụ thể hay khẳng định mọi kết luận đều tìm được trong thời gian hữu hạn.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="declarative-facts-relations">Dữ kiện logic (fact) và các quan hệ đã biết</a>

<details>
<summary>Xem chi tiết</summary>

**Dữ kiện logic (fact)** là phát biểu về một quan hệ được chấp nhận là đúng trong cơ sở tri thức: `parent(An, Binh)` nghĩa là An là cha/mẹ của Bình. Đây không phải **sự kiện xảy ra theo thời gian** như “người dùng vừa bấm nút”.

Các dữ kiện có thể nối thành mạng quan hệ: `parent(Binh, Chi)` bổ sung một cạnh mới trong quan hệ cha/mẹ. Dữ liệu này cho phép đặt câu hỏi về quan hệ mà không gắn cách tìm kiếm vào từng dữ kiện.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="declarative-inference-rules">Quy tắc suy ra và các điều kiện logic</a>

<details>
<summary>Xem chi tiết</summary>

Quy tắc suy ra diễn đạt rằng một kết luận đúng **khi các điều kiện liên quan đều đúng**. Ví dụ `grandparent(x,z)` nếu tồn tại `y` sao cho `parent(x,y)` và `parent(y,z)`. Quy tắc mô tả quan hệ ông/bà–cháu, không nêu thứ tự duyệt từng người.

Nếu một trong hai quan hệ cha/mẹ cần thiết không có cơ sở chứng minh, bộ suy diễn không thể kết luận quan hệ ông/bà theo quy tắc ấy. Cần phân biệt “chưa chứng minh được” với “đã chứng minh là sai”; ý nghĩa cụ thể phụ thuộc ngữ nghĩa của hệ logic.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="declarative-goal-queries">Mục tiêu truy vấn và kết luận phù hợp</a>

<details>
<summary>Xem chi tiết</summary>

**Mục tiêu (goal)** là quan hệ hoặc mệnh đề người dùng muốn kiểm tra. Với hai fact `parent(An,Binh)` và `parent(Binh,Chi)`, truy vấn ông/bà của Chi có thể tìm An nhờ quy tắc suy ra.

Có hệ thống chỉ trả lời đúng/sai, có hệ thống còn tìm giá trị của biến trong mục tiêu. Một mục tiêu có thể có nhiều lời giải; muốn chọn lời giải duy nhất phải có tiêu chí bổ sung. Đây là cách suy nghĩ về câu hỏi quan hệ, chưa phải bài cú pháp Prolog.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="declarative-search-boundary">Đặc tả quan hệ so với thuật toán tìm kiếm</a>

<details>
<summary>Xem chi tiết</summary>

Quy tắc `grandparent` diễn đạt **kết luận phụ thuộc quan hệ nào**, không nói engine phải duyệt người A trước người B. Bộ suy diễn vẫn phải thực hiện một chiến lược tìm kiếm hoặc suy luận để trả lời câu hỏi.

Chiến lược khác nhau ảnh hưởng tài nguyên, thứ tự trả lời và khả năng kết thúc, nhất là với quan hệ đệ quy. Vì vậy không được suy từ hình thức khai báo ra tuyên bố rằng mọi cách tìm kiếm tương đương về hiệu năng hoặc kết thúc.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="declarative-prolog-boundary">Giới hạn giữa ý tưởng logic và cú pháp/trình giải Prolog</a>

<details>
<summary>Xem chi tiết</summary>

Prolog là một ví dụ nổi bật của hệ thống sử dụng dữ kiện, quy tắc và mục tiêu truy vấn. Nó có cách hợp nhất biến, chọn mệnh đề và tìm lời giải riêng; các chi tiết đó giúp hiện thực quan hệ nhưng không phải định nghĩa duy nhất của lập trình khai báo.

Trong module này, dạng `parent(x,y)` chỉ là ký hiệu minh họa quan hệ; không hướng dẫn chạy trình thông dịch hoặc cú pháp đầy đủ. Nếu nghiên cứu Prolog, cần học riêng thứ tự quy tắc, cách tìm kiếm và những trường hợp truy vấn không kết thúc.

</details>

- [Quay lại đầu trang](#back-to-top)
