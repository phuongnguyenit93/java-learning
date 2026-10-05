# Spring WebFlux

Module này giải thích Spring WebFlux như web stack reactive, non-blocking của Spring Framework. Mục tiêu không phải học lại Reactive Streams hay Project Reactor từ đầu, mà là hiểu cách Spring áp dụng reactive model vào HTTP server processing, endpoint programming, cross-cutting web infrastructure và outbound HTTP.

Trước khi học, learner nên nắm Java, HTTP request/response cơ bản và các khái niệm reactive nền tảng như publisher, subscriber, asynchronous composition và backpressure. Reactive programming tổng quát thuộc module Reactive Programming; module này chỉ sở hữu các mechanics đặc thù của WebFlux.

Flow học của module:

1. hiểu vì sao WebFlux tồn tại và khi nào non-blocking model mang lại giá trị;
2. học reactive HTTP runtime, WebHandler chain, DispatcherHandler và result-processing architecture;
3. hiểu request/response body, codec, streaming, multipart, validation integration và HTTP caching behavior;
4. học annotated reactive controller;
5. học functional endpoint với WebFlux.fn;
6. hiểu filter, Reactor Context, session, error layer, configuration và controlled blocking execution;
7. học WebClient cùng lifecycle của resource thuộc HTTP connector bên dưới;
8. tổng hợp production trade-off, failure mode, handoff sang module lân cận và end-to-end request flow.

Module dừng rõ ở ownership boundary. Validation, binding, conversion và formatting dùng chung thuộc Spring Framework Validation and Data Binding. Reactive persistence thuộc Spring Framework Data Access và Spring Data. Reactive transaction semantics thuộc Transaction Management. Framework-level testing thuộc Spring Testing. Spring WebSocket client/server abstractions, WebSocket handler/session lifecycle, STOMP và application messaging cấp cao hơn thuộc Spring Messaging. Reactor và Reactive Streams theory tổng quát vẫn thuộc Reactive Programming.

Sau khi hoàn thành, learner nên có thể theo dõi một WebFlux request từ HTTP adapter qua dispatch và endpoint handling tới response completion, giải thích hệ quả của blocking work và resource usage, chọn Spring MVC hay WebFlux dựa trên constraint thực tế, và xác định đúng owner khi một concern đi ra ngoài WebFlux request-response boundary.
