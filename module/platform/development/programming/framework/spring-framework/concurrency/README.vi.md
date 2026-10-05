# 📂 README MODULE STRUCTURE (VI)

* **1.MentalModel**
    * [SpringConcurrency](readme/vi/menu/1.MentalModel/SpringConcurrency.md)
* **2.Spring_Task_Executor**
    * [SpringExecutor](readme/vi/menu/2.Spring_Task_Executor/SpringExecutor.md)
* **3.ContextPropagation**
    * [ContextPropagation](readme/vi/menu/3.ContextPropagation/ContextPropagation.md)
* **4.TaskScheduler**
    * [TaskScheduler](readme/vi/menu/4.TaskScheduler/TaskScheduler.md)
* **5.Scheduling**
    * [Scheduling](readme/vi/menu/5.Scheduling/Scheduling.md)
* **6.VirtualThreadIntegration**
    * [VirtualThreadIntegration](readme/vi/menu/6.VirtualThreadIntegration/VirtualThreadIntegration.md)
* **7.LifecycleFailureProduction**
    * [LifecycleFailureProduction](readme/vi/menu/7.LifecycleFailureProduction/LifecycleFailureProduction.md)

# Spring Framework Concurrency

Spring Framework Concurrency giải thích cách Spring quản lý việc thực thi tác vụ, gọi method bất đồng bộ và lập lịch theo thời gian trên nền các primitive của Java Concurrency. Module tập trung vào abstraction của framework, lifecycle do container quản lý, cơ chế proxy của `@Async`, context propagation, hành vi của scheduler và các integration point của Spring Framework 6.1 với JDK 21 Virtual Thread.

Mục tiêu không phải học lại thread, executor, future, synchronization hay Java Memory Model. Những kiến thức đó là prerequisite từ Java Concurrency. Ở đây người học xây dựng mental model để chọn, cấu hình và vận hành executor/scheduler do Spring quản lý một cách đúng đắn.

## Kiến thức cần có trước

- Java Concurrency fundamentals: thread, executor, future, synchronization, thread pool và kiến thức nền về Virtual Thread.
- Spring Core Container: bean, dependency injection, configuration và managed lifecycle.
- Spring AOP basics khi phân tích proxy-based `@Async` và self-invocation.
- Reactive Programming fundamentals chỉ khi học reactive `@Scheduled` method.

## Flow học

1. **Mô hình concurrency trong Spring** — phân biệt task execution, async invocation và scheduling, đồng thời giữ rõ ranh giới Spring/JDK.
2. **Thực thi tác vụ và `@Async`** — học `TaskExecutor`, `ThreadPoolTaskExecutor`, async proxy dispatch, chọn executor, kiểu trả về và cách quan sát lỗi.
3. **Context Propagation** — hiểu vì sao thread-bound context bị mất và cách `TaskDecorator` cùng context-propagation support của Spring 6.1 xử lý boundary này an toàn.
4. **TaskScheduler và lập lịch bằng API** — xây mô hình scheduling quanh `TaskScheduler`, `Trigger`, lựa chọn implementation, cancellation, rescheduling và lifecycle.
5. **Lập lịch khai báo với `@Scheduled`** — học trigger mode, cron/time-zone semantics, scheduler qualifier, overlap, failure và reactive scheduled method.
6. **Tích hợp Virtual Thread** — so sánh virtual-thread executor/scheduler của Spring với pooled platform thread và hiểu caveat của fixed-delay.
7. **Vòng đời, lỗi và tư duy production** — tổng hợp shutdown, saturation, failure model, diagnostics, handoff sang Spring Boot và quyết định executor/scheduler end-to-end.

## Ranh giới module

Module này sở hữu abstraction thực thi tác vụ và lập lịch của Spring Framework. Nó không sở hữu Java concurrency model nền, lý thuyết AOP/proxy tổng quát, Spring Boot task auto-configuration, Reactive Streams theory hay Quartz internals.

Spring Boot có thể cung cấp executor/scheduler default tiện dụng, nhưng các default đó thuộc curriculum của Spring Boot. Quartz chỉ xuất hiện ở đây như decision boundary cho bài toán cần capability vượt quá mô hình `TaskScheduler` chạy trong process.

Baseline của learning journey này là Spring Framework **6.1.14** trên Java **21**.
