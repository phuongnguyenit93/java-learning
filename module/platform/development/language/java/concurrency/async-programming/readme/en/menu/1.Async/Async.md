<a id="back-to-top"></a>

# Future, CompletableFuture, and Asynchronous Programming

## Menu
- [1. Async does not mean Non-blocking](#async-mental-model)
- [2. Callable and Future](#future)
- [3. CompletableFuture: completion pipeline](#completable-future-pipeline)
- [4. thenApply and thenApplyAsync](#async-variant)
- [5. AllOf, anyOf and semantics of results](#combine-race)
- [6. Exception handling in the pipeline](#exception-handling)
- [7. Common pool and custom executor](#executor-choice)
- [8. Timeout and cancellation](#timeout-cancellation)
- [9. Experiments of the Async section](#async-experiments)

This section learns how to coordinate **Task Results**, after understanding Thread Pool/Executor.

Mental model:

```text
Executor
→ execute the task

Future / CompletableFuture
→ represent the completion/result of the task
```

## <a id="async-mental-model">1. Async does not mean Non-blocking</a>

<details>
<summary>Click for details</summary>

Asynchronous describes the relationship between callers and jobs:

```text
Caller Submit Work
→ don't necessarily wait for the work to be completed right at the submission point
```

But the worker executing that job can still block it.

For example:

```java
CompletableFuture.supplyAsync(() -> blockingJdbcCall());
```

Callers can be freed from JDBC calls, but workers running lambda can still be blocked by JDBC.

Therefore, it is necessary to distinguish:

| Concept | Question |
| --- | --- |
| Async | Does Caller have to wait for completion right away? |
| Parallel | Are there multiple jobs that actually run simultaneously? |
| Blocking | Is the executable thread held in the meantime? |
| Non-blocking | Does the operation avoid holding the Thread while waiting for an external event? |

</details>

- [Back to top](#back-to-top)

---

## <a id="future">2. Callable and Future</a>

<details>
<summary>Click for details</summary>

`Callable<T>` The task description has a result/exception.

```java
Future<String> future = executor.submit(callable);
```

`Future` Allows:

- Inspection `isDone()`;
- `cancel(...)`;
- Get the result by `get()`;
- `get(timeout, unit)`.

Cut-off Points: `future.get()` is the caller's blocking wait.

References:

```text
AsyncController#future()
GET /async/future
```

Demo keep unfinished tasks, confirm `isDone=false`, then let the task finish running and call `get()`.

**Final Thoughts** `Future` detach submit from result retrieval, but call `get()` can still be blocked.

### <a id="future-cancellation">Future cancellation and mayInterruptIfRunning</a>

`Future.cancel(...)` change the completion state of Future to cancelled if the cancellation is accepted. Parameters:

```java
future.cancel(false);
future.cancel(true);
```

should not be construed as:

```text
false = task definitely stop gently
true = JVM kill task immediately
```

The mental model is more correct:

```text
cancel(false)
→ doesn't require a running interrupt task
→ task that has been run can continue to completion naturally

cancel(true)
→ implementation can interrupt a Thread that is running a task
→ task still has to cooperate with interruption
```

After Future has cancelled, `get()` do not return the result of the task but throw it `CancellationException`, including the case of underlying code with `cancel(false)` still continue to run to the end.

References:

```text
AsyncController#futureCancellation()
GET /async/future-cancellation
```

Experiment runs two tasks that are blocked at `CountDownLatch`:

```text
cancel(false)
→ Future cancelled
→ worker is not interrupted
→ task remains blocked until the experiment is actively released

cancel(true)
→ Future cancelled
→ worker is waiting to receive an InterruptedException
→ Task Ends via Cancellation Path
```

This is directly connected to the Interruption chapter:

```text
Future cancellation
→ can request interruption
→ interruption is still a cooperative protocol
```

</details>

- [Back to top](#back-to-top)

---

## <a id="completable-future-pipeline">3. CompletableFuture: completion pipeline</a>

<details>
<summary>Click for details</summary>

`CompletableFuture` Allows for continuation instead of forcing the caller to `get()` step by step.

Important method groups:

### Transformation

```text
thenApply
thenApplyAsync
```

### Flat composition

```text
thenCompose
thenComposeAsync
```

Used when a callback returns a `CompletionStage` and want to avoid nested futures.

### Combine two independent branches

```text
thenCombine
thenCombineAsync
```

### Side effect

```text
thenAccept
thenRun
```

References:

```text
AsyncController#pipeline()
GET /async/pipeline
```

Pipeline demo:

```text
order task ───────────┐
                     ├─ thenCombine → total
exchange-rate task ──┘
                           ↓
                       thenApply
```

The first two branches use a local custom executor; the executor is shut down by completion callback.

### get() and join() are different in the exception model

Both `get()` and `join()` can all wait for completion, but the surface API is different:

```text
get()
→ checked InterruptedException
→ checked ExecutionException when computation fail

join()
→ not declaring a checked exception
→ exceptional completion is exposed via CompletionException
→ cancelled future can throw a CancellationException
```

`join()` does not mean non-blocking. If the stage is not complete, the caller may still have to wait.

In experiment `allOf()`, call code `all.get(...)` with a timeout to bounded wait the entire barrier first, then use the `join()` on each completed future to get the typed result. This is an API selection, not two different execution mechanisms.

</details>

- [Back to top](#back-to-top)

---

## <a id="async-variant">4. thenApply and thenApplyAsync</a>

<details>
<summary>Click for details</summary>

Don't learn the rules:

> `thenApplyAsync` definitely run on a different Thread stage first.

More Useful Rules:

- non-async dependent action can be executed by Thread completion stage or Thread joining completion;
- The Async variant is submitted for execution according to the default Async facility or `Executor` specified;
- The specific worker name is not a contract that it is required to be different from the previous worker stage.

If the execution policy is important, pass the executor explicitly:

```java
future.thenApplyAsync(this::transform, executor);
```

References:

```text
AsyncController#executionVariant()
GET /async/execution-variant
```

The experiment uses a completed stage to show `thenApply` can run inline on the caller, while `thenApplyAsync(..., executor)` is dispatched via the specified executor. The conclusion to be kept is **Execution Policy**, not assuming that every async variant always runs on a different Thread stage first.

</details>

- [Back to top](#back-to-top)

---

## <a id="combine-race">5. AllOf, anyOf and semantics of results</a>

<details>
<summary>Click for details</summary>

`allOf(...)` completion when all future inputs are complete.

`allOf(...)` Pay `CompletableFuture<Void>`; If the result typed of each future is needed, the application must still keep the reference and get the result of each stage after the barrier completion is complete.

"Completed" here does not mean "successful". If an input future completes exceptionally, the aggregate future of `allOf(...)` also complete exceptionally after the required inputs have been completed under the contract.

The point that is easy to mistake is `allOf()` **Not a fail-fast barrier** in the sense that "a child fail is an aggregate that is immediately completed". For example:

```text
A fail first
B is not yet complete

allOf(A, B)
→ aggregate is not yet complete

B complete later
→ now aggregate complete exceptionally
```

If the business needs to fail-fast orchestration and actively cancel the remaining work, the application must develop its own policy instead of inferring from `allOf()`.

`anyOf(...)` Completed when **A Future First** completed.

First completion can be success **or failure**. If the first future is complete exceptionally then aggregate `anyOf(...)` can also be completed exceptionally immediately; it doesn't ignore failure to wait for another future success.

That means:

```text
anyOf = fastest completion
```

Not:

```text
anyOf = best business result
```

Example of two servers bidding:

```text
Server A pre-completed: $200
Server B completed later: $190
```

`anyOf` can pay $200 because A is faster; it doesn't know that $190 is the "better price."

Failure race is similar:

```text
Server A fails first
Server B success after

anyOf(A, B)
→ first completion is failure
→ aggregate can be complete exceptionally
```

Also `allOf()` / `anyOf()` is **Completion Aggregation**, not a cancellation policy. It should not be assumed that if the aggregate completes/fails, the rest of the sibling futures will be canceled on their own. If the business needs winner-cancels-losers or fail-fast with cancellation, the application must design its own cancellation/cleanup.

References:

```text
AsyncController#fastest()
GET /async/fastest

AsyncController#allOf()
GET /async/all-of
```

Experiment controls the order of completions using coordination signals instead of relying on `sleep()` timing. Server A is allowed to complete before, after `anyOf` select the winner, then Server B will be released and cleaned up before the experiment ends.

The Experiment also creates a small race that doesn't require timing: one future is completed exceptionally first, the other succeeds later. Response confirming exceptional completion can be won `anyOf` and Losing Futures are not automatically canceled.

`allOf()` also run a case with a controlled completion order: input A fails before sibling B completes. The response confirms that the aggregate is incomplete immediately after the first failure, only completes after the other sibling is complete, and then exposes exceptional completion without canceling the sibling itself:

```text
aggregateCompletedImmediatelyAfterFirstFailure = false
aggregateWaitedForRemainingSibling = true
aggregateExceptionalWhenInputFails = true
successfulSiblingCancelledAutomatically = false
```

**Final Thoughts** Primitive Coordination does not replace the Business Selection Rule.

</details>

- [Back to top](#back-to-top)

---

## <a id="exception-handling">6. Exception handling in the pipeline</a>

<details>
<summary>Click for details</summary>

Three common APIs:

### Exceptionally

Only run when the previous stage fails and returns a fallback value.

### handle

Get Both `(result, exception)` and can always turn outcomes into new value.

### whenComplete

Observe success/failure but is usually not used to transform outcomes.

References:

```text
AsyncController#exceptionHandling()
GET /async/exception
```

Demo created failure `handle()` into a controlled result.

</details>

- [Back to top](#back-to-top)

---

## <a id="executor-choice">7. Common pool and custom executor</a>

<details>
<summary>Click for details</summary>

Overloads that do not transmit the executor of the async factory/async continuation usually use the default async facility of `CompletableFuture`, commonly related `ForkJoinPool.commonPool()`.

Don't let a large workload block accidentally occupy the common pool without understanding the impact on other features in the process.

Custom executors are useful when needed:

- resource isolation;
- queue/capacity;
- thread naming;
- observability;
- shutdown policy;
- context propagation.

</details>

- [Back to top](#back-to-top)

---

## <a id="timeout-cancellation">8. Timeout and cancellation</a>

<details>
<summary>Click for details</summary>

A production pipeline needs to think about:

- timeout;
- cancellation;
- propagation of interruption/cancellation to underlying work;
- partial failure;
- cleanup.

APIs such as `orTimeout(...)` and `completeOnTimeout(...)` Helps perform the timeout at the stage level, but it should not be assumed that the future timeout will force all blocking operations below it to stop immediately.

Distinguish three things:

```text
get(timeout)
→ only limit the time the caller waits for the result

orTimeout / completeOnTimeout
→ changes to CompletableFuture's completion semantics

Future.cancel(true)
→ cancellation request can be accompanied by the interruption of the running task.

CompletableFuture.cancel(true)
→ cancel completion state
→ MayInterruptIfRunning parameter does not control CompletableFuture's underlying processing
```

None of the above three mechanics are a primitive "kill Thread no matter what the code is doing".

References:

```text
AsyncController#timeoutCancellation()
GET /async/timeout-cancellation
```

The experiment runs two separate cases, each with its own executor/latch and cleanup bounded:

```text
orTimeout
→ future complete with TimeoutException
→ underlying workers don't claim to interrupt themselves

CompletableFuture.cancel(true)
→ future moves to cancelled state
→ join() throws a CancellationException
→ worker is still not interrupted by cancel(true)
→ experiment must actively release underlying work
```

This is an important distinction with `Future` Red `ExecutorService.submit(...)` returned in the previous part: cannot be derived from words `cancel(true)` that every implementation of `Future` all have the same interruption behavior.

</details>

- [Back to top](#back-to-top)

---

## <a id="async-experiments">9. Experiments of the Async section</a>

<details>
<summary>Click for details</summary>

| README section | Controller method | Endpoint |
| --- | --- | --- |
| `#future` | `AsyncController#future()` | `GET /async/future` |
| `#future-cancellation` | `AsyncController#futureCancellation()` | `GET /async/future-cancellation` |
| `#completable-future-pipeline` | `AsyncController#pipeline()` | `GET /async/pipeline` |
| `#async-variant` | `AsyncController#executionVariant()` | `GET /async/execution-variant` |
| `#combine-race` | `AsyncController#fastest()` | `GET /async/fastest` |
| `#combine-race` | `AsyncController#allOf()` | `GET /async/all-of` |
| `#exception-handling` | `AsyncController#exceptionHandling()` | `GET /async/exception` |
| `#timeout-cancellation` | `AsyncController#timeoutCancellation()` | `GET /async/timeout-cancellation` |

</details>

- [Back to top](#back-to-top)
