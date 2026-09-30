# Cách một chương trình Java được tổ chức

Trước khi học sâu các quy tắc về giá trị hay kiểu dữ liệu, người mới cần biết **một đoạn mã Java nằm ở đâu trong cấu trúc chương trình**. Phần này chỉ xây dựng bản đồ để đọc mã; chi tiết về lớp/đối tượng sẽ được mở rộng ở mô-đun riêng.

## <a id="source-file-type-structure">Tệp mã nguồn và khai báo kiểu</a>

Mã Java được viết trong các tệp nguồn `.java`. Một tệp có thể chứa khai báo `package`, các `import` và một hoặc nhiều khai báo kiểu theo các quy tắc của ngôn ngữ.

Ví dụ tối giản:

```java
package demo;

import java.util.List;

public class App {
    public static void main(String[] args) {
        System.out.println("Hello");
    }
}
```

Ở mức nền tảng, hãy đọc cấu trúc từ ngoài vào trong:

```text
tệp mã nguồn
→ package/import
→ khai báo kiểu
→ thành viên
→ thân phương thức/constructor
→ câu lệnh/biểu thức
```

`package` và `import` mới chỉ được nhận diện ở đây để đọc được tệp nguồn. Cơ chế không gian tên, phân giải tên và quyền truy cập trong cùng `package` được học ở chặng riêng phía sau.

## <a id="statement-expression-model">Câu lệnh và biểu thức</a>

**Biểu thức (expression)** tạo ra một giá trị hoặc tham gia tính toán một giá trị. **Câu lệnh (statement)** mô tả một hành động hoặc một bước điều khiển chương trình.

```java
int total = price * quantity;
```

Trong ví dụ này, `price * quantity` là biểu thức; toàn bộ khai báo biến kết thúc bằng `;` là một câu lệnh.

Không phải mọi biểu thức đều đứng độc lập thành một câu lệnh, và một câu lệnh có thể chứa nhiều biểu thức. Phân biệt hai khái niệm này giúp người học hiểu rõ hơn toán tử, điều kiện, vòng lặp và lời gọi phương thức ở các chặng sau.

## <a id="lexical-elements">Định danh, từ khóa, giá trị viết trực tiếp và chú thích</a>

Java còn có những thành phần nhỏ giúp tạo nên mã nguồn:

- **định danh (identifier)**: tên do lập trình viên đặt cho biến, phương thức, lớp...;
- **từ khóa (keyword)**: từ dành riêng có ý nghĩa cú pháp như `class`, `if`, `return`, `new`; một số từ có thể là **định danh bị giới hạn (restricted identifier)** trong những ngữ cảnh được Java quy định;
- **giá trị viết trực tiếp (literal)**: cách viết một giá trị ngay trong mã như `10`, `true`, `'A'`, `"Java"`;
- **chú thích mã nguồn (comment)**: nội dung dành cho người đọc, không trở thành hành vi thực thi của chương trình. Java hỗ trợ `// ...`, `/* ... */` và dạng tài liệu `/** ... */`.

Mục tiêu ở đây không phải học thuộc toàn bộ từ khóa hay ngữ pháp, mà là có đủ từ vựng để nhìn một chương trình nhỏ và nhận ra từng phần đang đóng vai trò gì.
