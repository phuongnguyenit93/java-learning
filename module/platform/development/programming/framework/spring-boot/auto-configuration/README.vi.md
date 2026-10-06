# 📂 README MODULE STRUCTURE (VI)

* **1.AutoConfigurationFoundation**
    * [AutoConfigurationFoundation](readme/vi/menu/1.AutoConfigurationFoundation/AutoConfigurationFoundation.md)
* **2.ActivationDiscovery**
    * [ActivationDiscovery](readme/vi/menu/2.ActivationDiscovery/ActivationDiscovery.md)
* **3.ConditionModel**
    * [ConditionModel](readme/vi/menu/3.ConditionModel/ConditionModel.md)
* **4.BackOffUserControl**
    * [BackOffUserControl](readme/vi/menu/4.BackOffUserControl/BackOffUserControl.md)
* **5.OrderingComposition**
    * [OrderingComposition](readme/vi/menu/5.OrderingComposition/OrderingComposition.md)
* **6.ExclusionsDiagnostics**
    * [ExclusionsDiagnostics](readme/vi/menu/6.ExclusionsDiagnostics/ExclusionsDiagnostics.md)
* **7.CustomAuthoring**
    * [CustomAuthoring](readme/vi/menu/7.CustomAuthoring/CustomAuthoring.md)
* **8.AutoConfigurationTesting**
    * [AutoConfigurationTesting](readme/vi/menu/8.AutoConfigurationTesting/AutoConfigurationTesting.md)
* **9.StarterSynthesis**
    * [StarterSynthesis](readme/vi/menu/9.StarterSynthesis/StarterSynthesis.md)

# Spring Boot Auto-configuration

Spring Boot auto-configuration là lớp quy ước tự đóng góp hạ tầng Spring phù hợp khi classpath, cấu hình, bean definition và runtime context của ứng dụng đáp ứng các điều kiện tương ứng. Cơ chế này loại bỏ phần wiring hạ tầng lặp lại nhưng vẫn giữ quyền kiểm soát cho ứng dụng thông qua condition và back-off.

Module giả định learner đã hiểu Spring container ở mức cơ bản và mô hình ứng dụng Spring Boot. Hãy bắt đầu từ mục đích và luồng end-to-end, sau đó tách rõ candidate discovery với condition evaluation, lý giải back-off và ordering, chẩn đoán quyết định của Boot, rồi áp dụng mô hình vào custom auto-configuration, context test tập trung và starter design.

Flow khuyến nghị: nền tảng → kích hoạt/khám phá → condition → back-off → ordering/composition → exclusion/diagnostics → authoring → focused testing → starter synthesis.

Module này sở hữu hành vi auto-configuration của Spring Boot. Cơ chế bean/container tổng quát vẫn thuộc Spring Framework; semantics của property source và binding thuộc Externalized Configuration; chiến lược kiểm thử Boot tổng quát thuộc Testing; build/dependency management thuộc Build Tooling and Packaging; AOT/native-image chuyên sâu thuộc Native Image.
