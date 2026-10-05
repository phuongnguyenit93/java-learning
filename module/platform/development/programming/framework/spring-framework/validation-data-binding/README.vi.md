# 📂 README MODULE STRUCTURE (VI)

* **1.Foundation**
    * [Foundation](readme/vi/menu/1.Foundation/Foundation.md)
* **2.PropertyAccess**
    * [PropertyAccess](readme/vi/menu/2.PropertyAccess/PropertyAccess.md)
* **3.TypeConversion**
    * [TypeConversion](readme/vi/menu/3.TypeConversion/TypeConversion.md)
* **4.Formatting**
    * [Formatting](readme/vi/menu/4.Formatting/Formatting.md)
* **5.Validation**
    * [Validation](readme/vi/menu/5.Validation/Validation.md)
* **6.DataBinder**
    * [DataBinder](readme/vi/menu/6.DataBinder/DataBinder.md)
* **7.BeanValidation**
    * [BeanValidation](readme/vi/menu/7.BeanValidation/BeanValidation.md)
* **8.SafeBinding**
    * [SafeBinding](readme/vi/menu/8.SafeBinding/SafeBinding.md)

# Spring Validation và Data Binding

Module này giải thích hạ tầng dùng chung của Spring Framework để biến dữ liệu bên ngoài hoặc dữ liệu dạng text thành trạng thái object có kiểu, kiểm tra tính hợp lệ của trạng thái đó và biểu diễn lỗi theo cấu trúc thống nhất.

Phạm vi chính gồm property access, type conversion, field formatting, Spring validation, DataBinder orchestration, tích hợp Jakarta Bean Validation và các quyết định binding an toàn.

## Vì sao module này tồn tại?

Nếu không có một mô hình binding và validation dùng chung, mỗi layer sẽ phải tự định nghĩa cách chuyển đổi text, áp giá trị lên object, biểu diễn lỗi và quyết định object sau cùng có hợp lệ hay không.

Spring tách các concern này thành những contract có thể tái sử dụng ngoài một transport hoặc UI framework cụ thể.

## Luồng học

1. Hiểu vì sao validation, binding, conversion và formatting là các concern khác nhau.
2. Học mô hình property access và binding result.
3. Học hệ thống type conversion tổng quát của Spring.
4. Bổ sung field formatting và parse/print phụ thuộc locale.
5. Học Spring Validator, structured errors, validation hints và message-code resolution.
6. Kết nối các phần qua DataBinder, gồm constructor binding, property binding và declarative binding của Spring Framework 6.1.
7. Tích hợp Jakarta Bean Validation và executable method validation.
8. Tổng hợp thành mô hình quyết định binding an toàn end-to-end.

## Kiến thức cần có trước

Learner nên nắm object Java thông thường, property, constructor, annotation, exception và các khái niệm cơ bản của Spring container.

## Ranh giới module

Module này sở hữu các contract Spring Framework dùng chung như Validator, Errors, BindingResult, DataBinder, ConversionService, họ Converter, Formatter infrastructure và lớp tích hợp Bean Validation của Spring.

Module chủ động dừng trước binding phụ thuộc transport:

- Spring MVC sở hữu WebDataBinder, controller argument binding, @InitBinder và vòng đời validation phụ thuộc HTTP.
- Spring WebFlux sở hữu vòng đời binding/validation của reactive controller.
- Spring Core Container sở hữu MessageSource và i18n sau khi module này đã tạo structured errors cùng message codes có thể resolve.
- Spring AOP sở hữu cơ chế proxy/interceptor được dùng bởi cách wiring method validation dựa trên proxy.

## Kết quả mong đợi

Sau khi hoàn thành module, learner nên chọn được đúng cơ chế conversion, formatting, binding và validation; giải thích được cách DataBinder điều phối chúng; hiểu declarative binding của Spring Framework 6.1; và thiết kế được luồng an toàn từ raw input tới application object có kiểu và đã được validate.
