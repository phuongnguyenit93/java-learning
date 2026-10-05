<a id="back-to-top"></a>

# Declarative Scheduling with @Scheduled

## Menu
- [@EnableScheduling and Scheduled Method Registration](#scheduled-infrastructure)
- [Periodic and One-Time Scheduling: Fixed Delay, Fixed Rate, Cron, and Initial Delay](#scheduled-trigger-modes)
- [Cron Expressions, Time Zones, and Time Units](#scheduled-cron-zone-timeunit)
- [Choosing a Scheduler with @Scheduled](#scheduled-scheduler-qualifier)
- [Repeatable Schedules and Overlap](#scheduled-overlap)
- [Scheduled Task Failure Behavior](#scheduled-failure)
- [Reactive @Scheduled Methods](#reactive-scheduled-methods)
- [SchedulingConfigurer and Advanced Registration](#scheduling-configurer)

## <a id="scheduled-infrastructure">@EnableScheduling and Scheduled Method Registration</a>

<details>
<summary>Click for details</summary>

`@EnableScheduling` activates Spring's annotation-driven scheduling infrastructure. Internally, a `ScheduledAnnotationBeanPostProcessor` discovers eligible `@Scheduled` methods and registers tasks with a scheduler.

This is a different mental model from `@Async`. There does not need to be a caller invoking the method through a proxy on every firing. The container discovers scheduling metadata while processing the bean and creates a scheduled registration.

A normal synchronous `@Scheduled` method has no arguments. Its return value, if any, is ignored; the useful contract is the side effect performed at each scheduled execution. Reactive methods are a Spring 6.1 exception discussed later because Spring schedules repeated subscriptions to the returned publisher.

If no scheduler is explicitly configured, the scheduling infrastructure attempts to resolve a suitable scheduler from the context, preferring a unique `TaskScheduler` or the conventional bean name `taskScheduler` and also supporting a `ScheduledExecutorService`. If no suitable bean exists, the annotation processor can create a local single-threaded default scheduler.

That fallback is convenient, not a production sizing recommendation. Once scheduled work can block or overlap, define the scheduler deliberately so its execution and lifecycle policy is visible.

</details>

- [Back to top](#back-to-top)

---

## <a id="scheduled-trigger-modes">Periodic and One-Time Scheduling: Fixed Delay, Fixed Rate, Cron, and Initial Delay</a>

<details>
<summary>Click for details</summary>

`@Scheduled` supports both periodic and one-time registration.

For periodic work, choose one primary trigger model:

- `fixedDelay` — the next delay is measured after the previous invocation completes.
- `fixedRate` — executions are scheduled according to a fixed period between scheduled start times.
- `cron` — execution follows a Spring cron expression and an optional time zone.

`initialDelay` postpones the first firing. In Spring Framework 6.1, `initialDelay` may also be used **without** fixed delay, fixed rate, or cron to create a one-time scheduled task.

Examples:

```java
@Scheduled(fixedDelay = 5, timeUnit = TimeUnit.SECONDS)
void pollAfterCompletion() { ... }

@Scheduled(fixedRate = 1, timeUnit = TimeUnit.MINUTES)
void sampleEveryMinute() { ... }

@Scheduled(initialDelay = 10, timeUnit = TimeUnit.SECONDS)
void runOnceAfterStartupDelay() { ... }
```

Do not infer overlap or non-overlap from `fixedRate` alone. With the traditional `ThreadPoolTaskScheduler`, one periodic registration inherits `ScheduledThreadPoolExecutor` semantics, so its successive fixed-rate executions do not overlap themselves; if one run takes too long, subsequent runs are delayed. With `SimpleAsyncTaskScheduler`, fixed-rate firings are normally handed to separate execution threads and may overlap. Separate registrations — including repeatable `@Scheduled` declarations — are independent and may overlap regardless.

</details>

- [Back to top](#back-to-top)

---

## <a id="scheduled-cron-zone-timeunit">Cron Expressions, Time Zones, and Time Units</a>

<details>
<summary>Click for details</summary>

Spring cron expressions use six fields, including **seconds**:

```text
second minute hour day-of-month month day-of-week
```

For example, `0 0 9 * * MON-FRI` means 09:00 on weekdays according to the selected scheduler clock/time zone. The `zone` attribute lets a cron declaration use an explicit time zone instead of the scheduler's default zone.

Numeric `fixedDelay`, `fixedRate`, and `initialDelay` values use the `timeUnit` attribute, whose default is milliseconds:

```java
@Scheduled(fixedRate = 30, timeUnit = TimeUnit.SECONDS)
```

String variants can be externalized and may also use duration-style values where supported. The `timeUnit` attribute is ignored for cron expressions and for duration strings that already carry their own unit. Keep units explicit enough that a configuration value such as "30" cannot be mistaken for seconds when it is actually milliseconds.

Cron has one useful operational convention: `Scheduled.CRON_DISABLED` is the value `"-"`. A placeholder can resolve to that marker to disable a cron trigger without deleting the annotation. That is convenient for configuration-driven enable/disable behavior, but the application should still make it obvious operationally that the job is disabled.

Time zones, daylight-saving transitions, and business calendars are requirements, not formatting details. If a job has legal or business-time meaning, test those calendar transitions explicitly.

</details>

- [Back to top](#back-to-top)

---

## <a id="scheduled-scheduler-qualifier">Choosing a Scheduler with @Scheduled</a>

<details>
<summary>Click for details</summary>

Spring Framework 6.1 adds the `scheduler` attribute to `@Scheduled`. It lets one scheduled method select a specific scheduler by qualifier or bean name instead of relying on the default scheduler resolution.

```java
@Scheduled(
    fixedRate = 10,
    timeUnit = TimeUnit.SECONDS,
    scheduler = "latencySensitiveScheduler"
)
void refreshFastPath() { ... }
```

The qualifier is matched against a specific `TaskScheduler` or `ScheduledExecutorService` bean definition by qualifier value or bean name. This is useful when workloads genuinely need different policies — for example, one small latency-sensitive job should not share scheduler threads with a slow blocking maintenance job.

Keep the reason architectural. Multiple schedulers mean multiple thread resources, lifecycle policies, observability surfaces, and capacity settings. Creating one scheduler per method just to obtain different thread names usually increases operational complexity without adding useful isolation.

An empty `scheduler` attribute means "use the normal default scheduler resolution." If a method requires a dedicated policy for correctness or latency, make that dependency explicit and give the scheduler a name that describes the policy rather than a random implementation detail.

</details>

- [Back to top](#back-to-top)

---

## <a id="scheduled-overlap">Repeatable Schedules and Overlap</a>

<details>
<summary>Click for details</summary>

`@Scheduled` is repeatable. Multiple `@Scheduled` declarations on the same method are processed independently, and Spring explicitly allows the resulting schedules to overlap or execute in close succession.

Overlap can occur when:

- `SimpleAsyncTaskScheduler` dispatches a later fixed-rate/cron firing while an earlier execution is still running;
- several bean instances each register the same scheduled method;
- multiple application instances run the same in-process schedule;
- a method has several independent cron/rate declarations.

By contrast, increasing the pool size of `ThreadPoolTaskScheduler` does **not** make successive executions of one fixed-rate/fixed-delay registration overlap with themselves; the JDK periodic-task contract serializes those executions. A larger scheduler pool allows different scheduled registrations to run concurrently.

Therefore `@Scheduled` is **not a distributed lock or single-flight guarantee**.

If overlapping executions are unsafe, choose the required protection explicitly: make the job idempotent, serialize it through a single-thread scheduler where that is sufficient, use application-level locking, or use a distributed scheduling/locking mechanism when several processes participate.

Be careful with stateful scheduled beans. Concurrent invocations of the same singleton method may access the same fields. Thread safety is still ordinary Java concurrency responsibility; Spring scheduling does not make mutable state safe.

</details>

- [Back to top](#back-to-top)

---

## <a id="scheduled-failure">Scheduled Task Failure Behavior</a>

<details>
<summary>Click for details</summary>

A scheduled failure needs a policy that preserves future scheduling without hiding operational problems.

For repeating `Runnable` tasks, Spring's default scheduling error strategy logs the error and suppresses it so later executions can continue. A one-shot task can instead propagate the failure through its future/underlying execution path. `ThreadPoolTaskScheduler` also allows a custom `ErrorHandler` when the application needs a different policy.

That default is intentionally different from "retry the failed business operation immediately". The next scheduled firing is not necessarily a retry of the same logical work. If a job mutates external state, retry semantics require idempotency, duplicate detection, backoff, and business-specific decisions.

```text
scheduled callback throws
→ ErrorHandler / scheduler failure policy observes it
→ repeating registration normally remains eligible for future firings
```

Do not catch `Exception` inside every job and silently continue merely to keep the schedule alive; that removes useful failure evidence. Handle only failures for which the job has a deliberate recovery path, and let the scheduling error channel record unexpected failures.

Reactive `@Scheduled` methods use a different error path: publisher `onError` is logged and recovered so future subscriptions continue, and the scheduler's ordinary `ErrorHandler` is not the reactive error channel.

</details>

- [Back to top](#back-to-top)

---

## <a id="reactive-scheduled-methods">Reactive @Scheduled Methods</a>

<details>
<summary>Click for details</summary>

Spring Framework 6.1 allows `@Scheduled` methods to return a reactive `Publisher` or a type that Spring can adapt to a Publisher **with deferred subscription semantics**.

The lifecycle is easy to misread. Spring obtains the Publisher from the method once, then schedules repeated **subscriptions** to that Publisher according to the trigger:

```text
bean processing
→ invoke scheduled method to obtain Publisher
→ register schedule
      ↓ each firing
   subscribe again
```

This means the Publisher must be safe for repeated subscription and should defer the actual work until subscription. An asynchronously completed type whose adapter is not deferred — notably `CompletableFuture` — is not suitable for this reactive scheduled contract.

Emitted `onNext` values are ignored because scheduling is about triggering work, not consuming a result stream. If a subscription terminates with `onError`, Spring logs the error at WARN level and recovers so later scheduled subscriptions still occur; the regular scheduler `ErrorHandler` is not involved in that reactive error.

For fixed-delay reactive scheduling, Spring blocks the subscription to preserve "delay after completion" semantics. On context shutdown, Spring cancels scheduled tasks and active reactive subscriptions associated with them.

This section owns only the Spring scheduling boundary. Deferred execution, backpressure, Reactor Context, and publisher design belong to the Reactive Programming/Spring Reactive curricula.

</details>

- [Back to top](#back-to-top)

---

## <a id="scheduling-configurer">SchedulingConfigurer and Advanced Registration</a>

<details>
<summary>Click for details</summary>

Implement `SchedulingConfigurer` when annotation defaults are not enough and the application needs direct access to the `ScheduledTaskRegistrar` used for registration.

A configuration class can use the registrar to:

- select/configure the scheduler;
- add fixed-rate, fixed-delay, cron, or custom `Trigger` tasks programmatically;
- register schedules whose timing is calculated dynamically;
- configure the `ObservationRegistry` used for Spring 6.1 scheduled-task observations.

```java
@Configuration
@EnableScheduling
class SchedulingConfig implements SchedulingConfigurer {
    @Override
    public void configureTasks(ScheduledTaskRegistrar registrar) {
        registrar.setScheduler(taskScheduler());
        registrar.addTriggerTask(this::runJob, triggerContext -> nextTime(triggerContext));
    }
}
```

This is the right level when schedule registration itself is application logic. It is also the natural bridge for runtime rescheduling because the application can keep `ScheduledTask`/`ScheduledFuture` handles rather than pretending annotation attributes are mutable.

Avoid registering the same logical job both through `@Scheduled` and programmatically unless duplicate scheduling is intentional. The registrar is infrastructure; business logic should still live in ordinary services that the scheduled callback invokes.

</details>

- [Back to top](#back-to-top)
