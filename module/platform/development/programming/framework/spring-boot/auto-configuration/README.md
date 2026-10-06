# 📂 README MODULE STRUCTURE (EN)

* **1.AutoConfigurationFoundation**
    * [AutoConfigurationFoundation](readme/en/menu/1.AutoConfigurationFoundation/AutoConfigurationFoundation.md)
* **2.ActivationDiscovery**
    * [ActivationDiscovery](readme/en/menu/2.ActivationDiscovery/ActivationDiscovery.md)
* **3.ConditionModel**
    * [ConditionModel](readme/en/menu/3.ConditionModel/ConditionModel.md)
* **4.BackOffUserControl**
    * [BackOffUserControl](readme/en/menu/4.BackOffUserControl/BackOffUserControl.md)
* **5.OrderingComposition**
    * [OrderingComposition](readme/en/menu/5.OrderingComposition/OrderingComposition.md)
* **6.ExclusionsDiagnostics**
    * [ExclusionsDiagnostics](readme/en/menu/6.ExclusionsDiagnostics/ExclusionsDiagnostics.md)
* **7.CustomAuthoring**
    * [CustomAuthoring](readme/en/menu/7.CustomAuthoring/CustomAuthoring.md)
* **8.AutoConfigurationTesting**
    * [AutoConfigurationTesting](readme/en/menu/8.AutoConfigurationTesting/AutoConfigurationTesting.md)
* **9.StarterSynthesis**
    * [StarterSynthesis](readme/en/menu/9.StarterSynthesis/StarterSynthesis.md)

# Spring Boot Auto-configuration

Spring Boot auto-configuration is the convention layer that contributes sensible Spring infrastructure when the application's classpath, configuration, bean definitions, and runtime context make those defaults appropriate. It removes repetitive infrastructure wiring while preserving application control through conditions and back-off.

This module assumes basic Spring container and Spring Boot application knowledge. Learn the purpose and end-to-end flow first, then separate candidate discovery from condition evaluation, reason about back-off and ordering, diagnose decisions, and finally apply the model to custom auto-configuration, focused context tests, and starter design.

Recommended flow: foundation → activation/discovery → conditions → back-off → ordering/composition → exclusions/diagnostics → authoring → focused testing → starter synthesis.

The module owns Spring Boot's auto-configuration behavior. General bean/container mechanics remain in Spring Framework; property-source and binding semantics remain in Externalized Configuration; broad Boot testing remains in Testing; build/dependency-management mechanics remain in Build Tooling and Packaging; AOT/native-image depth remains in Native Image.
