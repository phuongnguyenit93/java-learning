# Imperative Programming

## <a id="imperative-what">1. Imperative Programming là gì?</a>

Imperative Programming là paradigm mô tả chương trình như một chuỗi **command thay đổi state** theo thời gian.

```text
state hiện tại
→ command
→ state mới
→ command tiếp theo
```

## <a id="imperative-why">2. Tại sao nó tồn tại?</a>

Máy tính thực thi instruction theo trình tự và thường cập nhật memory/state trong quá trình chạy. Imperative Programming phản ánh khá trực tiếp mô hình đó nên dễ dùng để mô tả algorithm từng bước.

## <a id="imperative-before">3. Nó giải quyết vấn đề gì?</a>

Khi bài toán cần kiểm soát rõ thứ tự thao tác, branch, loop và mutation, imperative style cho phép viết chính xác từng bước hệ thống phải thực hiện.

## <a id="imperative-solution">4. Mental model</a>

```text
do A
then B
if condition → do C
repeat D
update state
```

Các concept thường gặp gồm assignment, mutable state, control flow, procedure và explicit sequencing.

## <a id="imperative-boundary">5. Boundary với các paradigm khác</a>

Imperative không đối lập tuyệt đối với OOP hay Functional Programming. Một chương trình OOP vẫn có thể rất imperative; functional code cũng có thể chứa imperative section.

Đây là một axis về **cách mô tả computation**, không phải nhãn loại trừ lẫn nhau.
