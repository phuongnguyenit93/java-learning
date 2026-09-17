<a id="back-to-top"></a>

# Spring TaskExecutor, ThreadPoolTaskExecutor, and @Async

## Menu
- [1. What is TaskExecutor?](#task-executor)
- [2. @Async works via Spring proxies](#async-annotation)
- [3. Return type of @Async](#async-return-type)
- [4. Configure ThreadPoolTaskExecutor](#spring-executor-config)
- [5. TaskDecorator and context propagation](#task-decorator)
- [6. Demo @Async + custom executor + context](#spring-async-demo)
- [7. Spring Framework and Spring Boot to distinguish](#spring-boot-auto-config)
- [8. Lifecycle and shutdown](#executor-lifecycle)
- [9. Experiment by Spring Task Executor](#spring-executor-experiments)

Spring does not replace Java concurrency primitives. It provides abstraction and lifecycle integration to make their use more convenient in the application.

```text
Java Executor concepts
        ↓
Spring TaskExecutor / ThreadPoolTaskExecutor
        ↓
@Async proxy dispatch
```

## <a id="task-executor">1. What is TaskExecutor?</a>

<details>
<summary>Click for details</summary>

`org.springframework.core.task.TaskExecutor` is Spring's abstraction for task execution.

It's close to `Executor` of Java but integrated into the Spring container.

`ThreadPoolTaskExecutor` is a common implementation, below use `ThreadPoolExecutor` and expose configurations such as:

- core pool size;
- max pool size;
- queue capacity;
- keep-alive;
- thread name prefix;
- rejection handler;
- TaskDecorator;
- shutdown behavior.

The mental model is still the part Thread Pool has learned:

```text
core → queue → max → rejection
```

Spring doesn't change this ground rule.

A spring-specific nuance is the exception type in the abstraction boundary. `ThreadPoolTaskExecutor` Spring Implementation `TaskExecutor` contract; when a task is rejected, the caller should handle it according to the Spring rejection semantics as `TaskRejectedException` instead of writing code that depends on that RAW `RejectedExecutionException` of JDK always passes constantly.

If you need a custom overload policy at the JDK layer, underlying `ThreadPoolExecutor` still use `RejectedExecutionHandler`; but the public contract that the application calls through `TaskExecutor` is a contract of Spring.

</details>

- [Back to top](#back-to-top)

---

## <a id="async-annotation">2. @Async works via Spring proxies</a>

<details>
<summary>Click for details</summary>

First of all, the application must enable the async method execution infrastructure. In the module:

```java
@Configuration
@EnableAsync
class TaskExecutorConfig {
}
```

`@EnableAsync` requires Spring to register the infrastructure required for the detect/intercept method to have `@Async` According to the current async configuration.

Mental model:

```text
@EnableAsync
→ enable async method execution infrastructure

@Async
→ mark the method invocation that needs to be handled by the async interceptor

TaskExecutor
→ Practical execution strategy
```

`@Async` standalone is not a Java language feature and does not manually turn the method call to asynchronous if the application context does not enable or configure the corresponding async support.

When the method is called via the Spring proxy:

```text
caller
→ proxy intercept @Async method
→ submit invocation to TaskExecutor
→ caller receives Control/Result Handle
→ worker execution method
```

Therefore, `@Async` not Java keywords and do not create their own "miracles" inside the method.

### Self-invocation

If a method in the bean calls a `@Async` Other methods on the main `this`, the call may not pass through the proxy, so async interception does not occur under the default proxy mode.

Design is usually clearer when the async boundary is between two spring beans.

</details>

- [Back to top](#back-to-top)

---

## <a id="async-return-type">3. Return type of @Async</a>

<details>
<summary>Click for details</summary>

Common types:

- `Void` for fire-and-forget;
- `Future<T>`;
- `CompletableFuture<T>`.

With `Void`, caller does not have a direct completion handle; exception handling also needs to be tailor-made.

In proxy-based `@Async`, exception of `Void` The method cannot be received by the caller `Future`. If the application is truly fire-and-forget, it is required `AsyncUncaughtExceptionHandler`/logging/metric strategy instead of assuming an exception will return itself to the HTTP caller.

With `CompletableFuture`, callers can compose results according to the concepts learned in the Async section.

The code currently uses the result-bearing method:

```text
TaskExecutorService#runAsync(...)
```

so that the caller has a clear completion handle and the experiment can observe deterministic.

### Demo exception of @Async void

With `Void`, Spring proxy cannot return exceptions to callers through `Future` because the caller does not receive any completion handles.

Configuration One `AsyncUncaughtExceptionHandler` Specifically:

```text
AsyncExceptionProbe
```

And an async method deliberately fails:

```java
@Async("threadLearningTaskExecutor")
public void failWithoutFuture(String correlationId) {
    throw new IllegalStateException(...);
}
```

References:

```text
TaskExecutorController#asyncVoidException()
GET /spring-executor/void-exception
```

The controller uses a correlation id just so that the learning experiment can observe the deterministic that the handler has received the correct failure. The expected response looks like this:

```text
handlerInvoked = true
method = failWithoutFuture
exceptionType = IllegalStateException
correlationId = ...
```

Probe also cleanup registration when observation future completes exceptional or timeout. Controller mounted timeout **Before** call the async proxy and also catch a synchronous submission failure. This is important because the task can be rejected at the submission boundary, before the `@Async` method actually runs and before `AsyncUncaughtExceptionHandler` Have a chance to receive an exception from Method Body.

The flow of the experiment is:

```text
Register Observation Future
→ attach timeout
→ call proxy @Async
   ├─ Successfully submit → worker running → handler complete future
   └─ submit rejected → caller receiving submission failure sync
                       → complete future exceptionally
→ all terminal paths are cleanup pending registration
```

Therefore, it is necessary to distinguish between **Task failure after dispatch** with **Failure at Submission/Admission**. `AsyncUncaughtExceptionHandler` Resolve Exit Exception `void @Async` Method body; it is not a replacement for rejection handling at the submission boundary.

**Final Thoughts** `Void @Async` is fire-and-forget for callers; error reporting must go through logging/metrics/`AsyncUncaughtExceptionHandler` or a separate channel. If the business flow needs to know the success/failure, prioritize the return `CompletableFuture`/result handle instead of `Void`.

</details>

- [Back to top](#back-to-top)

---

## <a id="spring-executor-config">4. Configure ThreadPoolTaskExecutor</a>

<details>
<summary>Click for details</summary>

Config uses its own bean:

```text
threadLearningTaskExecutor
```

so that the experiments use an executor with a clear and easy-to-observe configuration.

The demo values are small because the goal is to learn semantics, not production sizing.

Sizing production still has to rely on workload/downstream/SLO like the Thread Pool.

Refer to the code:

```text
TaskExecutorConfig#threadLearningTaskExecutor()
```

### Demo config and production config are two different goals

Demo deliberately hard-coded small value:

```java
executor.setCorePoolSize(2);
executor.setMaxPoolSize(4);
executor.setQueueCapacity(8);
```

so that the experiment is easy to observe and does not depend on the environment.

Production should usually externalize the operational values that need to be tuned, for example:

```yaml
Application:
  async:
    core-pool-size: 8
    max-pool-size: 32
    queue-capacity: 500
    keep-alive-seconds: 60
    await-termination-seconds: 30
```

then bind with a configuration object:

```java
@ConfigurationProperties(prefix = "application.async")
public class AsyncExecutorProperties {
    private int corePoolSize;
    private int maxPoolSize;
    private int queueCapacity;
    private int keepAliveSeconds;
    private int awaitTerminationSeconds;
}
```

The config executor then takes the value from the properties instead of the hard-code.

Benefits:

- dev/staging/production can use different sizing without modifying the code;
- The deployment platform can be overridden by environment/config;
- operational tuning changes that are not mixed with business logic;
- It is easy to review clearly what are the semantics of the executor and what are the numbers that are suitable for the current workload.

But externalizing doesn't mean that every property should be allowed to be customized. Rejection policy, context propagation, or safety limits still need explicit default/validation to avoid a wrong config that alters the system semantics.

Mental model:

```text
Learning Demo
→ small, deterministic hard-code

production
→ typed configuration + validation
→ environment-specific values
→ metrics/load test for tuning
```

No copying of demo numbers `2/4/8` to production and also does not use the CPU-bound/I/O-bound formula as an absolute value.

</details>

- [Back to top](#back-to-top)

---

## <a id="task-decorator">5. TaskDecorator and context propagation</a>

<details>
<summary>Click for details</summary>

This is a direct bridge to the ThreadLocal section.

Pattern:

```text
request/caller thread
    context=REQUEST-123
        ↓ submit
TaskDecorator capture REQUEST-123
        ↓
Worker Thread
        ↓ restore REQUEST-123
Async Method Runs
        ↓ finally
restore/remove worker context
```

Demo Hold `DemoContext` to clearly see the capture/restore/cleanup mechanism, and at the same time propagate two more practical contexts commonly encountered in Spring applications:

- SLF4J MDC, for example `requestId` used for log correlation;
- Spring `RequestAttributes`, is the context associated with the current HTTP request.

References:

```text
DemoTaskDecorator
DemoContext
```

`DemoTaskDecorator` Capture all three contexts in the caller thread, restore them on Worker, and then restore/clear the old state in the `finally`.

The most important rule of a decorator is not just `Set`, which is **cleanup/restore in finally** because the worker will be reused for another task. If only the MDC/RequestAttributes are set without cleanup, the next task running on the same worker can see the context of the previous request.

`TaskDecorator` It should also not be treated as a universal async exception handler. Decorator receives a `Runnable` execution callback; the callback can be a wrapper created by the framework/executor instead of the original business lambda. With execution by `Future`/`FutureTask`, failure can be captured into the completion handle instead of always exiting directly from the `Runnable.run()` for the decorator to catch. So:

```text
TaskDecorator
→ context capture / restore / cleanup

Future / async handler / rejection handling
→ failure observation under the corresponding contract
```

Don't mix these two responsibilities just because they're around the execution boundary.

### Lifecycle of RequestAttributes

Propagation does not extend the lifecycle of an HTTP request. An async task can outlive the original request; then it should not assume every object/request-scoped state taken from `RequestAttributes` are still valid for discretionary use.

If the async work only needs a few values such as `requestId`, tenant ID or principal id, design is usually more secure and clear when captured **Essential Values** to a separate immutable context instead of keeping long-term dependencies on the request object.

</details>

- [Back to top](#back-to-top)

---

## <a id="spring-async-demo">6. Demo @Async + custom executor + context</a>

<details>
<summary>Click for details</summary>

Refer to the controller:

```text
TaskExecutorController#asyncWithContext()
GET /spring-executor/async-context
```

Controller Set:

```text
DemoContext=REQUEST-123
MDC[requestId] = REQUEST-123
RequestAttributes = current request managed by Spring Web
```

then call another bean that has:

```java
@Async("threadLearningTaskExecutor")
```

Response `CompletableFuture` said:

- caller thread;
- worker thread;
- DemoContext worker observed;
- MDC request id worker observed;
- whether the worker received Spring RequestAttributes.

Expectations:

```text
callerThread != workerThread
workerContext = REQUEST-123
workerMdcRequestId=REQUEST-123
workerHasRequestAttributes = true
```

The controller also captures context/MDC before setting the demo values and restores them in the `finally`. So the experiment doesn't lose the context that already exists on the caller thread.

</details>

- [Back to top](#back-to-top)

---

## <a id="spring-boot-auto-config">7. Spring Framework and Spring Boot to distinguish</a>

<details>
<summary>Click for details</summary>

It should not be said in general that:

> Without configuring the executor, `@Async` always use `SimpleAsyncTaskExecutor`.

That may be how fallbacks are described in the Spring Framework in some cases, but the application is using **Spring Boot 3.3.x**, where Boot has the task execution auto-configuration.

In the default configuration that does not use virtual threads, Boot usually provides `ThreadPoolTaskExecutor` for task execution if the application has not defined itself as a suitable executor.

When virtual threads are enabled using the corresponding boot configuration, the auto-configured task executor can switch to implementation using virtual threads.

Because of custom bean or `AsyncConfigurer` The last option can be changed, when behavior is critical, check the actual executor of the application instead of relying on a generic fallback sentence.

</details>

- [Back to top](#back-to-top)

---

## <a id="executor-lifecycle">8. Lifecycle and shutdown</a>

<details>
<summary>Click for details</summary>

A big advantage of Spring-managed executors is that containers can manage lifecycles.

`ThreadPoolTaskExecutor` There are shutdown-related options such as:

- wait for the task to be completed;
- timeout waiting for termination.

But "Spring management" doesn't mean that every task is definitely completed. Shutdown policy and timeout still have to match the SLA/deployment termination window.

Refer to the configuration of the module:

```text
TaskExecutorConfig#threadLearningTaskExecutor()
```

This method now configures both:

```text
setWaitForTasksToCompleteOnShutdown(true)
setAwaitTerminationSeconds(2)
```

Two small demo values to demo the lifecycle; production must match the termination window of the process/container and the actual task time.

</details>

- [Back to top](#back-to-top)

---

## <a id="spring-executor-experiments">9. Experiment by Spring Task Executor</a>

<details>
<summary>Click for details</summary>

| README section | Controller method | Endpoint |
| --- | --- | --- |
| `#spring-async-demo` | `TaskExecutorController#asyncWithContext()` | `GET /spring-executor/async-context` |
| `#async-return-type` | `TaskExecutorController#asyncVoidException()` | `GET /spring-executor/void-exception` |

</details>

- [Back to top](#back-to-top)
