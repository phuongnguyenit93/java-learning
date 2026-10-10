# 📂 README MODULE STRUCTURE (VI)

* **1.Introduction**
    * [AOP](readme/vi/menu/1.Introduction/AOP.md)
* **2.Terminology**
    * [Terminology](readme/vi/menu/2.Terminology/Terminology.md)
* **3.ImplementationModels**
    * [ImplementationModels](readme/vi/menu/3.ImplementationModels/ImplementationModels.md)
* **4.Tradeoffs**
    * [Tradeoffs](readme/vi/menu/4.Tradeoffs/Tradeoffs.md)

# Lập trình hướng khía cạnh (Aspect-Oriented Programming — AOP)

AOP là một cách tổ chức **mối quan tâm xuyên suốt** như ghi log, đo thời gian hoặc kiểm toán khi cùng một chính sách xuất hiện ở nhiều thành phần. AOP không thay thế logic nghiệp vụ hay toàn bộ lập trình hướng đối tượng: nó mô tả hành vi bổ sung và nơi hành vi đó được ghép vào luồng thực thi.

**Điểm xuất phát:** người học chỉ cần hiểu lời gọi hàm/phương thức, thứ tự thực thi và ý tưởng đối tượng cộng tác với trách nhiệm riêng. Mô hình OOP là nền tảng khuyến nghị; các thuật ngữ *aspect*, *join point*, *pointcut*, *advice* và *weaving* sẽ được giới thiệu từ đầu, không giả định đã biết Spring AOP.

**Cách học:** trước hết tìm hiểu bài toán logic dùng chung xen vào logic nghiệp vụ và so sánh với helper/wrapper/decorator tường minh. Sau đó học mô hình aspect–join point–pointcut–advice, cách ghép hành vi trước/sau/bao quanh lời gọi, những cách triển khai và giới hạn quan sát khác nhau. Phần cuối cân nhắc khả năng đọc hiểu, kiểm thử, truy vết và một tình huống chọn AOP hoặc giải pháp tường minh.

**Ranh giới:** module dạy tư duy AOP ở cấp paradigm. Cơ chế proxy và bean của Spring, cú pháp pointcut/AspectJ compiler hoặc weaver, và chi tiết transaction management nằm trong các module công nghệ tương ứng. Nguyên lý phân tách mối quan tâm rộng hơn AOP; tại đây chỉ tập trung vào phần *cross-cutting*.
