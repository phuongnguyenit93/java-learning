# 📂 README MODULE STRUCTURE (VI)

* **1.Introduction**
    * [FunctionalProgramming](readme/vi/menu/1.Introduction/FunctionalProgramming.md)
* **2.PureFunctions**
    * [PureFunctions](readme/vi/menu/2.PureFunctions/PureFunctions.md)
* **3.Immutability**
    * [Immutability](readme/vi/menu/3.Immutability/Immutability.md)
* **4.FunctionsComposition**
    * [FunctionsComposition](readme/vi/menu/4.FunctionsComposition/FunctionsComposition.md)
* **5.SideEffects**
    * [SideEffects](readme/vi/menu/5.SideEffects/SideEffects.md)
* **6.Tradeoffs**
    * [Tradeoffs](readme/vi/menu/6.Tradeoffs/Tradeoffs.md)

# Lập trình hàm

Lập trình hàm tổ chức phép tính thành quá trình **biến đổi đầu vào tường minh thành kết quả**. Cách tiếp cận này ưu tiên hàm thuần và giá trị bất biến để dễ dự đoán kết quả. Đây là **một lối tổ chức chương trình**, không phải yêu cầu sử dụng một ngôn ngữ hay thư viện Java cụ thể.

## Mục đích học

Khi nhiều phần của chương trình cùng sửa trạng thái hoặc tạo tác động phụ khó quan sát, việc thay đổi, kiểm thử và gỡ lỗi trở nên phức tạp. Lối hàm giúp giữ phần tính toán dễ suy luận, đồng thời chủ động quản lý thao tác vào/ra và trạng thái ứng dụng. Lối mệnh lệnh và hướng đối tượng vẫn hữu ích và có thể kết hợp với lập trình hàm.

## Điểm xuất phát

Người học cần đọc được giá trị, biến, điều kiện, tập dữ liệu và lời gọi hàm đơn giản. Kiến thức về lập trình mệnh lệnh và khai báo giúp so sánh, nhưng các thuật ngữ do module này sở hữu—hàm thuần, tính thay thế biểu thức bằng giá trị, hàm như giá trị và ghép hàm—được giới thiệu lại từ đầu. Không cần biết cú pháp lambda hoặc Java Stream API.

## Lộ trình

1. **Mục đích và mô hình tư duy:** đầu vào → biến đổi → đầu ra, động cơ và liên hệ với các lối lập trình đã biết.
2. **Hàm thuần:** đầu vào rõ ràng, tác động quan sát được, kết quả dự đoán được và tính thay thế biểu thức.
3. **Giá trị bất biến:** ảnh chụp trạng thái, thay đổi trạng thái ứng dụng, chia sẻ dữ liệu và chi phí đi kèm.
4. **Hàm như giá trị và ghép hàm:** hàm bậc cao, phép biến đổi có thể tái sử dụng và ý tưởng map/filter/reduce.
5. **Ranh giới tác động phụ:** tách phần tính toán khỏi vào/ra, dịch vụ bên ngoài, lỗi và điều phối trạng thái.
6. **Lựa chọn thiết kế:** cân nhắc hiệu năng, khả năng đọc, kiểm thử và cách phối hợp nhiều lối lập trình.

Các chương trong Menu chứa những bài học có anchor; phần tổng quan này không thay thế nội dung giảng giải của từng bài.

## Phạm vi và hướng học tiếp

Module dạy **tư duy lập trình hàm độc lập ngôn ngữ** qua các ví dụ khái niệm nhỏ. Functional interface, lambda, method reference, `Optional` và cơ chế Java Stream thuộc module ngôn ngữ Java. Luồng reactive thay đổi theo thời gian thuộc Reactive Programming; cách tách dữ liệu khỏi hành vi và dùng cấu trúc dữ liệu phổ dụng thuộc Data-Oriented Programming. Cơ chế luồng hoặc danh sách API thư viện không nằm trong phạm vi này.
