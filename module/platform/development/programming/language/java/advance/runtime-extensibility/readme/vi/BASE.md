# Runtime Extensibility

Runtime Extensibility là phần Java Advanced tập trung vào cách một host Java mở rộng hành vi lúc chạy thông qua **service contract**, **provider discovery** và **plugin boundary** thay vì hard-code mọi implementation. Module đi từ SPI và `ServiceLoader` tới plugin architecture, lifecycle, isolation, compatibility và `ModuleLayer`.

## Vì sao nên học module này?

Một application nhỏ có thể khởi tạo implementation trực tiếp. Khi hệ thống cần cho phép implementation được triển khai, lựa chọn hoặc thay thế độc lập, coupling đó trở thành giới hạn. Java cung cấp service-provider model và `ServiceLoader` để host phụ thuộc vào contract thay vì provider cụ thể; JPMS và `ModuleLayer` mở rộng mô hình này cho các plugin modular ở runtime.

Module này giúp phân biệt **discovery** với **selection**, **provider loading** với **plugin lifecycle**, và **runtime extensibility** với các cơ chế khác như class loading chuyên sâu hoặc instrumentation.

## Prerequisite

Nên nắm trước:

- Java Core: interface/abstract class, exception, resource lifecycle và collection/generics;
- ClassLoader ở mức class identity, visibility và delegation mental model;
- Reflection ở mức metadata cơ bản;
- JPMS cơ bản ở mức named module, `module-info.java`, `requires` và readability; module này sẽ đi sâu phần service directives (`uses` / `provides`), service binding và `ModuleLayer` cần cho runtime extensibility.

## Learning flow

Học theo thứ tự:

1. mental model của Runtime Extensibility và ranh giới module;
2. SPI/service-provider contract;
3. `ServiceLoader` discovery, lazy loading, cache, error và class-loader context;
4. provider discovery/selection strategy;
5. provider deployment trên class path và module path;
6. plugin architecture và extension points;
7. plugin lifecycle;
8. isolation và shared/private dependency boundary;
9. host-plugin version compatibility;
10. `Configuration` + `ModuleLayer` cho modular runtime plugins;
11. tổng hợp mechanism-selection và các design pitfall quan trọng.

## Ranh giới module

Runtime Extensibility sở hữu service-provider contract, `ServiceLoader`, provider discovery/selection, plugin architecture/lifecycle/isolation/compatibility và integration với class path, module path và `ModuleLayer`.

Các phần sau được handoff:

- class-loading lifecycle, delegation và custom loader mechanics chuyên sâu → Java Core ClassLoader;
- JPMS descriptor/readability/resolution curriculum đầy đủ → Java Version Module System;
- lịch sử ServiceLoader từ Java 6 → Java Version;
- framework-specific plugin systems → framework/module owner tương ứng;
- bytecode transformation và Java Agent → Instrumentation.

Mục tiêu cuối cùng là có thể thiết kế một extension flow end-to-end từ contract → deployment → discovery → selection → activation → isolation/failure handling, đồng thời chọn mức cơ chế đơn giản nhất đáp ứng đúng nhu cầu.
