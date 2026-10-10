# 📂 README MODULE STRUCTURE (VI)

* **1.DataOrientedProgramming**
    * [DataOrientedProgramming](readme/vi/menu/1.DataOrientedProgramming/DataOrientedProgramming.md)
* **2.CodeDataSeparation**
    * [CodeDataSeparation](readme/vi/menu/2.CodeDataSeparation/CodeDataSeparation.md)
* **3.GenericData**
    * [GenericData](readme/vi/menu/3.GenericData/GenericData.md)
* **4.ImmutableData**
    * [ImmutableData](readme/vi/menu/4.ImmutableData/ImmutableData.md)
* **5.DataSchema**
    * [DataSchema](readme/vi/menu/5.DataSchema/DataSchema.md)
* **6.DataFlow**
    * [DataFlow](readme/vi/menu/6.DataFlow/DataFlow.md)
* **7.JavaPerspective**
    * [JavaPerspective](readme/vi/menu/7.JavaPerspective/JavaPerspective.md)
* **8.Tradeoffs**
    * [Tradeoffs](readme/vi/menu/8.Tradeoffs/Tradeoffs.md)

# Lập trình hướng dữ liệu (DOP)

Lập trình hướng dữ liệu xem **dữ liệu ứng dụng là các giá trị có thể biểu diễn, quan sát và biến đổi độc lập với thao tác xử lý chúng**. Module tập trung vào bốn nguyên tắc theo Yehonathan Sharvit: tách mã xử lý khỏi dữ liệu; dùng cấu trúc dữ liệu phổ dụng; giữ dữ liệu bất biến; và tách phần mô tả cấu trúc (schema) khỏi dữ liệu.

## Mục đích và phạm vi

Khi dữ liệu gắn chặt với các đối tượng mang hành vi, việc dùng lại cùng thông tin cho nhiều cách xử lý có thể trở nên khó khăn. Thiết kế hướng dữ liệu giúp việc quan sát và biến đổi rõ ràng hơn, nhưng có những đánh đổi về đóng gói và kiểm tra kiểu tĩnh. Đây là một cách nhìn bổ sung, không phải sự phủ nhận lập trình hướng đối tượng hay lập trình hàm.

Tên **DOP** được dùng theo những hướng liên quan nhưng khác nhau. Trong Java/Project Amber, trọng tâm là mô hình dữ liệu minh bạch, bất biến và biểu diễn các trường hợp hợp lệ (thường minh họa bằng record, sealed hierarchy); theo Sharvit, trọng tâm là cấu trúc phổ dụng và schema độc lập. Hai hướng này đều khác **Data-Oriented Design (DOD)** chuyên tối ưu bố trí bộ nhớ, cache và thông lượng.

## Điểm xuất phát

Người học cần biết giá trị, hàm, tập dữ liệu và vai trò căn bản của đối tượng. Lập trình hướng đối tượng và lập trình hàm là nền tảng hữu ích để so sánh, nhưng module vẫn giải thích bốn nguyên tắc DOP từ đầu. Không cần biết record Java, schema của cơ sở dữ liệu, thư viện JSON Schema hay tối ưu phần cứng.

## Lộ trình học

1. **Mục đích và mô hình cốt lõi:** cách nhìn tập trung vào dữ liệu, động cơ, bốn nguyên tắc và ranh giới thuật ngữ.
2. **Tách mã xử lý khỏi dữ liệu:** hàm có thể tái sử dụng còn dữ liệu dễ quan sát.
3. **Cấu trúc dữ liệu phổ dụng:** map, danh sách, dữ liệu lồng nhau, tính linh hoạt và các rủi ro.
4. **Dữ liệu bất biến:** thay đổi bằng phiên bản dữ liệu mới nhưng trạng thái ứng dụng vẫn có thể tiến triển.
5. **Schema và kiểm tra hợp lệ:** mô tả cấu trúc độc lập và kiểm tra ở ranh giới tin cậy.
6. **Luồng ứng dụng hoàn chỉnh:** tiếp nhận, kiểm tra, biến đổi, chuyển kết quả và kiểm soát tác động phụ.
7. **Góc nhìn Java/Project Amber:** nhận diện cách mô hình hóa dữ liệu có kiểu, không học lại cú pháp Java.
8. **Đánh đổi thiết kế:** so sánh với cách tổ chức đối tượng và hàm; phân biệt DOP với DOD theo phần cứng.

Các chương trong Menu là nơi chứa bài học có anchor ổn định; phần này chỉ hướng dẫn đường học, không thay thế nội dung của từng bài.

## Ranh giới kiến thức

Cú pháp record/sealed/pattern thuộc module ngôn ngữ và phiên bản Java; mô hình miền DDD thuộc thiết kế phần mềm; SQL/ORM, tuần tự hóa JSON và lưu trữ dữ liệu thuộc module chuyên trách. Tối ưu cache, SIMD, bố trí bộ nhớ hoặc ECS **không phải** nội dung DOP được dạy ở đây. Ví dụ và diễn giải chi tiết đã nằm trong các bài Knowledge theo thứ tự Menu ở trên.
