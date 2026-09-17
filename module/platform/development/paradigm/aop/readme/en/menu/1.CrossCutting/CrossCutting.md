<a id="back-to-top"></a>

# AOP and Cross-Cutting Concerns

## Menu
- [1. Business concerns and cross-cutting concerns](#cross-cutting-concern)
- [2. Demo in this module](#cross-cutting-demo)
- [3. Conclusion](#cross-cutting-conclusion)

This section answers the most basic question: **why does AOP exist?**

## <a id="cross-cutting-concern">1. Business concerns and cross-cutting concerns</a>

<details>
<summary>Click for details</summary>

A business concern is the core logic of a use case, for example creating an order or canceling an order.

A cross-cutting concern is behavior that appears in many places but is not the main business logic, for example:

- logging;
- timing;
- auditing;
- tracing;
- authorization checks.

If every business method writes its own logging, the code is easily duplicated:

```text
createOrder()
→ log before
→ business logic
→ log after

cancelOrder()
→ log before
→ business logic
→ log after
```

AOP lets us extract that repeated behavior into an Aspect:

```text
LoggingAspect
        ↓
createOrder()
cancelOrder()
```

The important point is that AOP does not make business logic disappear. It only places cross-cutting behavior at an appropriate boundary.

</details>

- [Back to top](#back-to-top)

---

## <a id="cross-cutting-demo">2. Demo in this module</a>

<details>
<summary>Click for details</summary>

Controller:

```text
CrossCuttingController#observeCrossCutting()
```

Endpoint:

```text
GET /aop/cross-cutting/observe
```

Target methods:

```text
CrossCuttingService#createOrder()
CrossCuttingService#cancelOrder()
```

Cross-cutting behavior:

```text
CrossCuttingLoggingAspect#logAround(...)
```

The response `events` will look like:

```text
logging-before:createOrder
target:create-order
logging-after:createOrder
logging-before:cancelOrder
target:cancel-order
logging-after:cancelOrder
```

Open `CrossCuttingService` and verify that the two business methods do not write `logging-before` or `logging-after` themselves.

</details>

- [Back to top](#back-to-top)

---

## <a id="cross-cutting-conclusion">3. Conclusion</a>

<details>
<summary>Click for details</summary>

Mental model:

```text
Business method
→ focuses only on the main behavior

Aspect
→ contains shared behavior that cuts across multiple methods
```

AOP is a good fit when the concern is genuinely cross-cutting and the boundary can be described clearly with a pointcut or annotation.

</details>

- [Back to top](#back-to-top)
