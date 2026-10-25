# 📂 README MODULE STRUCTURE (EN)

* **1.AutoConfigurationFoundation**
    * [AutoConfigurationFoundation](content/readme/docs/en/menu/1.AutoConfigurationFoundation/knowledge/AutoConfigurationFoundation.md)
* **2.ActivationDiscovery**
    * [ActivationDiscovery](content/readme/docs/en/menu/2.ActivationDiscovery/knowledge/ActivationDiscovery.md)
* **3.ConditionModel**
    * [ConditionModel](content/readme/docs/en/menu/3.ConditionModel/knowledge/ConditionModel.md)
* **4.BackOffUserControl**
    * [BackOffUserControl](content/readme/docs/en/menu/4.BackOffUserControl/knowledge/BackOffUserControl.md)
* **5.OrderingComposition**
    * [OrderingComposition](content/readme/docs/en/menu/5.OrderingComposition/knowledge/OrderingComposition.md)
* **6.ExclusionsDiagnostics**
    * [ExclusionsDiagnostics](content/readme/docs/en/menu/6.ExclusionsDiagnostics/knowledge/ExclusionsDiagnostics.md)
* **7.CustomAuthoring**
    * [CustomAuthoring](content/readme/docs/en/menu/7.CustomAuthoring/knowledge/CustomAuthoring.md)
* **8.AutoConfigurationTesting**
    * [AutoConfigurationTesting](content/readme/docs/en/menu/8.AutoConfigurationTesting/knowledge/AutoConfigurationTesting.md)
* **9.StarterSynthesis**
    * [StarterSynthesis](content/readme/docs/en/menu/9.StarterSynthesis/knowledge/StarterSynthesis.md)

# Spring Boot Auto-configuration

Spring Boot auto-configuration is the convention layer that contributes sensible Spring infrastructure when the application's classpath, configuration, bean definitions, and runtime context make those defaults appropriate. It removes repetitive infrastructure wiring while preserving application control through conditions and back-off.

This module assumes basic Spring container and Spring Boot application knowledge. Learn the purpose and end-to-end flow first, then separate candidate discovery from condition evaluation, reason about back-off and ordering, diagnose decisions, and finally apply the model to custom auto-configuration, focused context tests, and starter design.

Recommended flow: foundation → activation/discovery → conditions → back-off → ordering/composition → exclusions/diagnostics → authoring → focused testing → starter synthesis.

The module owns Spring Boot's auto-configuration behavior. General bean/container mechanics remain in Spring Framework; property-source and binding semantics remain in Externalized Configuration; broad Boot testing remains in Testing; build/dependency-management mechanics remain in Build Tooling and Packaging; AOT/native-image depth remains in Native Image.
