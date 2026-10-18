# Spring Cache

Module này trình bày **Spring Framework Cache Abstraction** ở mức cơ chế của framework: cách Spring mô tả chính sách cache quanh lời gọi phương thức mà không buộc mã ứng dụng phụ thuộc trực tiếp vào một nhà cung cấp cache cụ thể.

Mục tiêu không phải là học Redis, Caffeine hay một sản phẩm cache phân tán cụ thể. Trọng tâm là hiểu:

- vì sao một kết quả nên hoặc không nên được cache;
- `Cache` và `CacheManager` đóng vai trò gì;
- `@Cacheable`, `@CachePut`, `@CacheEvict`, `@Caching`, `@CacheConfig` phối hợp ra sao;
- cache key, condition, `unless`, `CacheResolver`, mặc định toàn cục qua `CachingConfigurer` và ngữ nghĩa multi-cache;
- invalidation, dữ liệu cũ và khoảng trống nhất quán;
- ranh giới proxy/self-invocation của caching khai báo;
- xử lý đồng thời, `sync=true`, `CompletableFuture`, `Mono`, `Flux` trong Spring Framework 6.1;
- ranh giới provider, khả năng tương thích JCache và cập nhật cache theo transaction.

## Điều kiện tiên quyết

Người học nên hiểu trước:

- Spring IoC/container và vòng đời bean;
- mô hình tư duy cơ bản về lời gọi phương thức/proxy của Spring AOP;
- ngữ nghĩa equality/hash của Java cho object dùng làm cache key;
- khái niệm transaction cơ bản trước khi học cache theo transaction.

Không cần học Redis hoặc một nhà cung cấp cache cụ thể trước module này.

## Luồng học

```text
Mục đích và ranh giới của cache
        ↓
Trừu tượng Cache / CacheManager
        ↓
Caching khai báo bằng annotation
        ↓
Key / condition / lựa chọn vùng cache
        ↓
Cập nhật / invalidation / tính nhất quán
        ↓
Ranh giới chặn lời gọi qua proxy
        ↓
Xử lý đồng thời / sync / bất đồng bộ / reactive
        ↓
Nhà cung cấp và khả năng tương thích JCache
        ↓
Lỗi / cập nhật cache theo transaction
        ↓
Chiến lược cache toàn diện
```

## Nhóm chương

1. **Nền tảng** — mục đích của cache, ranh giới nguồn dữ liệu chuẩn, `Cache`, `CacheManager`.
2. **Khai báo chính sách** — annotation, tạo key, điều kiện và lựa chọn vùng cache.
3. **Tính đúng đắn** — cập nhật, eviction, invalidation, dữ liệu cũ và tính nhất quán.
4. **Cơ chế runtime** — proxy interception, self-invocation, xử lý đồng thời và kiểu trả về bất đồng bộ/reactive.
5. **Ranh giới tích hợp** — khả năng của nhà cung cấp, JCache, chính sách lỗi và thay đổi cache theo transaction.
6. **Tổng hợp** — xây dựng chiến lược cache hướng production mà không nhầm trừu tượng của Spring với ngữ nghĩa riêng của nhà cung cấp.

## Ranh giới module

Module này **sở hữu** trừu tượng Spring Cache và ngữ nghĩa caching khai báo.

Các phần sau được chuyển sang module hoặc tài liệu chuyên trách:

- cơ chế proxy/advice chuyên sâu → **Spring AOP**;
- transaction propagation/rollback/resource synchronization → **Transaction Management**;
- Redis TTL, serialization, topology, data structures → **Spring Data Redis**;
- thuật toán eviction, topology lưu trữ và tính nhất quán phân tán riêng của nhà cung cấp → tài liệu của nhà cung cấp cache tương ứng.

Spring Cache giúp ứng dụng diễn đạt chính sách nhất quán. Nó không biến các nhà cung cấp khác nhau thành cùng một mô hình runtime và cũng không tự cung cấp kho cache.
