# 📂 README MODULE STRUCTURE (VI)

* **1.Purpose**
    * [Purpose](readme/vi/menu/1.Purpose/Purpose.md)
* **2.Abstraction**
    * [Abstraction](readme/vi/menu/2.Abstraction/Abstraction.md)
* **3.ResourceSynchronization**
    * [ResourceSynchronization](readme/vi/menu/3.ResourceSynchronization/ResourceSynchronization.md)
* **4.Declarative**
    * [Declarative](readme/vi/menu/4.Declarative/Declarative.md)
* **5.TransactionPolicy**
    * [TransactionPolicy](readme/vi/menu/5.TransactionPolicy/TransactionPolicy.md)
* **6.Rollback**
    * [Rollback](readme/vi/menu/6.Rollback/Rollback.md)
* **7.Propagation**
    * [Propagation](readme/vi/menu/7.Propagation/Propagation.md)
* **8.Programmatic**
    * [Programmatic](readme/vi/menu/8.Programmatic/Programmatic.md)
* **9.Reactive**
    * [Reactive](readme/vi/menu/9.Reactive/Reactive.md)
* **10.EventsLifecycle**
    * [EventsLifecycle](readme/vi/menu/10.EventsLifecycle/EventsLifecycle.md)
* **11.BoundarySynthesis**
    * [BoundarySynthesis](readme/vi/menu/11.BoundarySynthesis/BoundarySynthesis.md)

# Spring Transaction Management

Module này dạy **cơ chế quản lý transaction của Spring Framework như một framework abstraction**, không học theo kiểu ghi nhớ danh sách option của `@Transactional` và cũng không thay thế kiến thức nền về transaction của database.

Mục tiêu chính là hiểu cách Spring biến transaction policy thành một ranh giới ứng dụng có thể dự đoán được trên cả mô hình imperative và reactive.

Learning journey của module:

```text
vì sao cần transaction management
        ↓
Spring transaction abstraction
        ↓
resource synchronization
        ↓
declarative transaction boundary
        ↓
transaction policy và rollback
        ↓
propagation
        ↓
programmatic control
        ↓
reactive transaction
        ↓
transaction-bound event và lifecycle hook
        ↓
tổng hợp boundary cho hệ thống thực tế
```

## Module này sở hữu gì?

Module là owner chính của:

- transaction abstraction và transaction-manager strategy của Spring;
- semantics của `@Transactional` và declarative demarcation;
- transaction policy như isolation, timeout, read-only, label và manager selection;
- rollback rule và rollback-only state;
- propagation và transaction participation;
- transaction-bound resource synchronization ở mức Spring Framework;
- programmatic transaction management;
- transaction context imperative và reactive;
- transaction-bound event và transaction execution listener;
- thiết kế transaction boundary trong ứng dụng thực tế.

## Kiến thức nền cần có

Learner nên nắm trước:

- Java exception và call stack;
- khái niệm transaction cơ bản của database như commit, rollback, isolation và savepoint;
- Spring container và bean ở mức cơ bản;
- mental model proxy/interceptor ở mức tổng quan;
- Reactor Context trước khi đi sâu vào reactive transaction.

Proxy mechanics chuyên sâu thuộc module **Spring AOP**. JDBC, ORM và R2DBC data-access mechanics thuộc **Spring Data Access / persistence**. Reactive programming fundamentals thuộc module **Reactive**.

## Bản đồ chapter

Module gồm mười một chapter:

1. **Vì sao cần quản lý transaction** — xây mental model về unit of work và boundary.
2. **Lớp trừu tượng transaction và chiến lược TransactionManager** — giới thiệu manager contract và execution model cốt lõi.
3. **Đồng bộ tài nguyên và tham gia transaction** — giải thích resource cùng tham gia một transaction như thế nào.
4. **Khai báo ranh giới transaction** — nối metadata, interception và proxy boundary.
5. **Thuộc tính transaction và thiết kế ranh giới** — biến annotation attribute thành transaction policy.
6. **Rollback và ngữ nghĩa lỗi** — giải thích quyết định commit/rollback và xử lý failure.
7. **Propagation và cách lời gọi tham gia transaction** — mô hình hóa logical và physical transaction scope.
8. **Quản lý transaction bằng code** — explicit control cho imperative và reactive flow.
9. **Mô hình transaction reactive** — chuyển từ thread-bound mental model sang Reactor Context.
10. **Sự kiện theo transaction và lifecycle hook** — phối hợp callback và observation theo transaction phase.
11. **Tổng hợp ranh giới transaction trong hệ thống thực tế** — ghép các concept thành quyết định thiết kế production.

## Boundary quan trọng

Module này chủ động không biến thành:

- tutorial JDBC hoặc ORM;
- catalog về mọi database isolation anomaly;
- khóa Spring AOP đầy đủ;
- khóa Reactor fundamentals;
- module Spring Boot transaction auto-configuration;
- module transactional testing;
- khóa distributed transaction hoặc distributed systems.

Các chủ đề đó chỉ được nhắc tới đủ để làm rõ semantics của Spring transaction.

## Cách học module

Không nên bắt đầu bằng việc học thuộc propagation constant hoặc annotation attribute.

Hãy dùng flow:

```text
xác định business unit of work
        ↓
chọn transaction boundary
        ↓
xác định transaction manager và resource tham gia
        ↓
hiểu commit / rollback semantics
        ↓
lý giải nested call và propagation
        ↓
kiểm tra thread/reactive context boundary
        ↓
phối hợp side effect sau transaction một cách tường minh
```

Kết quả mong đợi là learner có thể giải thích được **vì sao transaction bắt đầu, công việc nào tham gia, điều gì làm transaction commit hoặc rollback, guarantee dừng ở đâu và Spring mechanism nào chịu trách nhiệm tại từng điểm**.
