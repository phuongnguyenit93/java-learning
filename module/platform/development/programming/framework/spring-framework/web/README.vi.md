# 📂 README MODULE STRUCTURE (VI)

* **1.SpringMvcFoundation**
    * [SpringMvcFoundation](readme/vi/menu/1.SpringMvcFoundation/SpringMvcFoundation.md)
* **2.DispatcherServletLifecycle**
    * [DispatcherServletLifecycle](readme/vi/menu/2.DispatcherServletLifecycle/DispatcherServletLifecycle.md)
* **3.AnnotatedControllers**
    * [AnnotatedControllers](readme/vi/menu/3.AnnotatedControllers/AnnotatedControllers.md)
* **4.WebMvcFunctionalEndpoints**
    * [WebMvcFunctionalEndpoints](readme/vi/menu/4.WebMvcFunctionalEndpoints/WebMvcFunctionalEndpoints.md)
* **5.MvcBindingValidation**
    * [MvcBindingValidation](readme/vi/menu/5.MvcBindingValidation/MvcBindingValidation.md)
* **6.MessageConversionRepresentation**
    * [MessageConversionRepresentation](readme/vi/menu/6.MessageConversionRepresentation/MessageConversionRepresentation.md)
* **7.ViewRenderingNavigation**
    * [ViewRenderingNavigation](readme/vi/menu/7.ViewRenderingNavigation/ViewRenderingNavigation.md)
* **8.MvcErrorHandling**
    * [MvcErrorHandling](readme/vi/menu/8.MvcErrorHandling/MvcErrorHandling.md)
* **9.AsyncMvcStreaming**
    * [AsyncMvcStreaming](readme/vi/menu/9.AsyncMvcStreaming/AsyncMvcStreaming.md)
* **10.CrossCuttingRequestInfrastructure**
    * [CrossCuttingRequestInfrastructure](readme/vi/menu/10.CrossCuttingRequestInfrastructure/CrossCuttingRequestInfrastructure.md)
* **11.WebSupportResourceDelivery**
    * [WebSupportResourceDelivery](readme/vi/menu/11.WebSupportResourceDelivery/WebSupportResourceDelivery.md)
* **12.MvcConfigurationExtension**
    * [MvcConfigurationExtension](readme/vi/menu/12.MvcConfigurationExtension/MvcConfigurationExtension.md)
* **13.SynchronousHttpClients**
    * [SynchronousHttpClients](readme/vi/menu/13.SynchronousHttpClients/SynchronousHttpClients.md)
* **14.HttpServiceInterfaces**
    * [HttpServiceInterfaces](readme/vi/menu/14.HttpServiceInterfaces/HttpServiceInterfaces.md)
* **15.SpringWebSynthesis**
    * [SpringWebSynthesis](readme/vi/menu/15.SpringWebSynthesis/SpringWebSynthesis.md)

# Spring Framework Web

Module này dạy **các năng lực Web trên Servlet stack của Spring Framework**, tập trung vào Spring MVC và hạ tầng HTTP client đồng bộ của Spring.

Mục tiêu không phải là học thuộc annotation của controller. Module xây dựng mental model end-to-end:

```text
Servlet request
→ DispatcherServlet
→ chọn và gọi handler
→ tích hợp binding / validation
→ render representation hoặc view
→ exception resolution
→ hoàn tất response
```

Sau đó module nối mental model phía server với HTTP client đồng bộ và HTTP Service Interface của Spring.

## Kiến thức cần có trước

Learner nên đã hiểu:

- class, interface, annotation, exception và generic trong Java;
- khái niệm HTTP request/response cơ bản;
- Spring IoC container và mô hình ApplicationContext;
- nhận thức cơ bản về Servlet container và mô hình request/response của Servlet.

Nền tảng dùng chung của `Validator`, `DataBinder`, `ConversionService` và formatting thuộc module validation/data-binding. Module này tập trung vào cách Spring MVC **tích hợp và sử dụng** những cơ chế đó.

## Luồng học

```text
Spring MVC trên Servlet stack
        ↓
Vòng đời request qua DispatcherServlet
        ↓
Annotated controller + WebMvc.fn
        ↓
Tích hợp binding và validation trong MVC
        ↓
HTTP message conversion + view rendering
        ↓
Exception resolution + problem response
        ↓
Async MVC + streaming
        ↓
Filter / interceptor / CORS / web support
        ↓
Cấu hình MVC + extension point
        ↓
RestClient / RestTemplate
        ↓
HTTP Service Interface
        ↓
Tổng hợp Spring Web và các ranh giới
```

## Ranh giới module

Module này sở hữu phần Spring Framework mechanics chuyên sâu cho:

- Spring MVC và pipeline của `DispatcherServlet`;
- annotated endpoint và functional endpoint trên Servlet;
- tích hợp binding và validation đặc thù của MVC;
- `HttpMessageConverter`, content negotiation, view, redirect và resource delivery;
- MVC exception handling và problem response;
- Servlet async processing và streaming;
- filter, interceptor, CORS, forwarded header, multipart, locale và HTTP caching integration;
- MVC configuration và extension point;
- `RestClient`, `RestTemplate` và HTTP Service Interface ở mức Framework mechanics.

Module chủ động handoff:

- WebFlux và deep mechanics của `WebClient` → Spring Reactive;
- WebSocket, STOMP và Spring Messaging → Spring Messaging;
- nền tảng validation/data-binding dùng chung → Validation/Data Binding;
- MockMvc, TestContext và framework-level web testing → Spring Testing;
- authentication, authorization, SecurityFilterChain, CSRF và security policy → Spring Security;
- MVC auto-configuration và embedded-server behavior → Spring Boot;
- lý thuyết HTTP protocol, lựa chọn client, resilience và kiến trúc service-to-service → Integration HTTP.

## Kết quả mong đợi

Sau khi hoàn thành module, learner có thể theo dõi một Spring MVC request end-to-end, giải thích extension point tham gia ở đâu, phân biệt Servlet async với WebFlux, và sử dụng các HTTP client abstraction đồng bộ của Spring mà không nhầm ownership với những module lân cận.
