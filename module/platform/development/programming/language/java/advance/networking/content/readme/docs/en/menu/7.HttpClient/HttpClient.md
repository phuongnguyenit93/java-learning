<a id="back-to-top"></a>

# JDK HTTP Client

## Menu
- [The HttpClient, HttpRequest, and HttpResponse Model](#http-client-model)
- [HttpClient Configuration and Reuse](#http-client-configuration)
- [Building Requests, Headers, and Bodies](#http-request-building)
- [BodyHandler, BodySubscriber, and Response Handling](#http-response-handling)
- [send, sendAsync, and Completion Models](#http-sync-async)
- [Flow-Based Request/Response Body Streaming](#http-flow-streaming)
- [Version, Redirect, Timeout, Proxy, Authentication, and Cookies](#http-client-policies)
- [Connection Reuse and Resource Sharing](#http-client-resource-sharing)
- [Shutdown, Close, and Response-Body Lifetime](#http-client-lifecycle)
- [The Boundary Between JDK HTTP Client and HTTP Integration](#http-integration-boundary)

## <a id="http-client-model">The HttpClient, HttpRequest, and HttpResponse Model</a>

<details>
<summary>Click for details</summary>

`java.net.http.HttpClient` is the JDK's higher-level HTTP client API. Instead of opening sockets, framing HTTP, parsing status/header data, and managing connection reuse manually, application code works with three primary abstractions:

```text
HttpClient
   │  shared configuration + resources
   │
   ├── send/sendAsync(HttpRequest, BodyHandler)
   │                         │
   │                         └── decides how to consume the response body
   ↓
HttpResponse<T>
   ├── statusCode
   ├── headers
   └── body : T
```

`HttpRequest` describes a built request: URI, method, headers, timeout, preferred HTTP version, and an optional body publisher. `HttpClient` performs the exchange. `HttpResponse<T>` exposes response metadata together with the body type `T` selected by the `BodyHandler<T>`.

A basic example:

```java
HttpClient client = HttpClient.newHttpClient();

HttpRequest request = HttpRequest.newBuilder()
        .uri(URI.create("https://example.com/"))
        .GET()
        .build();

HttpResponse<String> response =
        client.send(request, HttpResponse.BodyHandlers.ofString());

System.out.println(response.statusCode());
System.out.println(response.body());
```

The generic type of `HttpResponse<T>` does not describe the payload on the wire. It describes the **Java representation after the body handler processes it**. The same HTTP response body can be represented as `String`, `byte[]`, `Path`, `InputStream`, `Flow.Publisher<...>`, or another type through an appropriate handler/subscriber.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="http-client-configuration">HttpClient Configuration and Reuse</a>

<details>
<summary>Click for details</summary>

Create an `HttpClient` through `HttpClient.newBuilder()` or `newHttpClient()`. Once built, the client is immutable and can send many requests. It is also the unit that shares HTTP configuration and resources.

```java
HttpClient client = HttpClient.newBuilder()
        .version(HttpClient.Version.HTTP_2)
        .followRedirects(HttpClient.Redirect.NORMAL)
        .connectTimeout(Duration.ofSeconds(5))
        .build();
```

Client-level settings such as preferred protocol version, redirect policy, connect timeout, proxy, authenticator, cookie handler, and executor apply across requests sent through that client. Some concerns can be refined at the request boundary, such as request timeout or preferred version.

The Oracle API documents that each `HttpClient` typically manages its own connection pool and may reuse connections when appropriate. Creating a new client for every operation therefore usually loses that reuse opportunity:

```text
GOOD ownership shape
one HttpClient
   ├── request A
   ├── request B
   └── request C

usually wasteful
new HttpClient -> one request -> close/drop
new HttpClient -> one request -> close/drop
```

"Reuse the client" does not mean sharing a mutable request builder across threads. The built `HttpClient` is the shared configuration/resource object; each logical exchange normally builds its own immutable `HttpRequest`.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="http-request-building">Building Requests, Headers, and Bodies</a>

<details>
<summary>Click for details</summary>

`HttpRequest.Builder` separates **request metadata** from **request-body production**. A simple GET can have no body; POST/PUT commonly receive a `BodyPublisher`.

```java
HttpRequest request = HttpRequest.newBuilder()
        .uri(URI.create("https://example.com/items"))
        .timeout(Duration.ofSeconds(10))
        .header("Content-Type", "application/json")
        .POST(HttpRequest.BodyPublishers.ofString(
                "{\"name\":\"book\"}", StandardCharsets.UTF_8))
        .build();
```

`BodyPublishers` provides common body sources:

- `noBody()` for a request without a body;
- `ofString(...)` and `ofByteArray(...)` for in-memory bodies;
- `ofFile(...)` for file content;
- `ofInputStream(...)` for a stream supplied on demand;
- `fromPublisher(...)` when the body comes from a `Flow.Publisher<ByteBuffer>`.

`header(name, value)` adds a header value; `setHeader(name, value)` replaces values for that name according to the builder contract. `HttpHeaders` on built requests and received responses is a read-only view.

A body publisher supplies body bytes. It does not automatically define content type, domain serialization, authentication scheme, or idempotency/retry policy; those concerns still belong to application or integration design.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="http-response-handling">BodyHandler, BodySubscriber, and Response Handling</a>

<details>
<summary>Click for details</summary>

A `BodyHandler<T>` decides **which Java type consumes the response body**. The handler is invoked after the status code and headers are available but before the actual body bytes are read, and it returns a `BodySubscriber<T>` for those bytes.

```text
status + headers arrive
        ↓
BodyHandler<T>.apply(ResponseInfo)
        ↓
BodySubscriber<T>
        ↓ consumes response bytes
CompletionStage<T>
        ↓
HttpResponse<T>.body()
```

Predefined handlers cover most common cases:

```java
BodyHandlers.ofString();       // HttpResponse<String>
BodyHandlers.ofByteArray();    // HttpResponse<byte[]>
BodyHandlers.ofFile(path);     // HttpResponse<Path>
BodyHandlers.discarding();     // consume and discard the body
BodyHandlers.ofInputStream();  // HttpResponse<InputStream>, body remains streaming
```

A handler can select a subscriber from response metadata. For example, materialize a text body only for status 200 and otherwise consume the body while replacing its value with an empty string:

```java
HttpResponse.BodyHandler<String> handler = info ->
        info.statusCode() == 200
                ? HttpResponse.BodySubscribers.ofString(StandardCharsets.UTF_8)
                : HttpResponse.BodySubscribers.replacing("");
```

`BodySubscriber<T>` extends `Flow.Subscriber<List<ByteBuffer>>` and turns the ordered response bytes into `T`. Prefer the predefined `BodyHandlers`/`BodySubscribers` when they fit; a custom subscriber must obey Flow demand/cancellation and resource-lifecycle rules.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="http-sync-async">send, sendAsync, and Completion Models</a>

<details>
<summary>Click for details</summary>

`send` and `sendAsync` use the same request/handler model but differ in how the caller observes completion.

`send(...)` blocks the calling thread until the response is available according to the body handler's semantics:

```java
HttpResponse<String> response =
        client.send(request, BodyHandlers.ofString());
```

`sendAsync(...)` returns a `CompletableFuture<HttpResponse<T>>` immediately:

```java
CompletableFuture<Integer> statusFuture = client
        .sendAsync(request, BodyHandlers.ofString())
        .thenApply(HttpResponse::statusCode);
```

Async here is a **completion API**. It does not make every downstream action non-blocking and does not require the caller to manage a `Selector`. The `CompletableFuture` represents an HTTP exchange/response-handling operation that will complete later.

The point at which the response/future becomes available also depends on the body handler. `ofString()` materializes the body as a `String`, whereas `ofInputStream()`, `ofLines()`, or `ofPublisher()` can expose the response after headers are available and leave further body consumption to the caller.

For the default JDK client, the future returned by `sendAsync` is cancelable; cancellation attempts to cancel the exchange but is best effort because processing may already have started. Likewise, interrupting `send` causes the default implementation to attempt cancellation and throw `InterruptedException`. Application code must not infer that successful Java-side cancellation proves that the remote server never saw the request.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="http-flow-streaming">Flow-Based Request/Response Body Streaming</a>

<details>
<summary>Click for details</summary>

JDK HTTP request and response bodies have a direct boundary with `java.util.concurrent.Flow`.

On the outgoing side, `HttpRequest.BodyPublisher` extends `Flow.Publisher<ByteBuffer>`. For each outgoing request body the client subscribes to the publisher to receive ordered byte buffers. `BodyPublishers.fromPublisher(...)` adapts an existing publisher:

```java
Flow.Publisher<ByteBuffer> source = createBodyPublisher();

HttpRequest request = HttpRequest.newBuilder(uri)
        .POST(HttpRequest.BodyPublishers.fromPublisher(source))
        .build();
```

On the incoming side, `BodySubscriber<T>` extends `Flow.Subscriber<List<ByteBuffer>>`. To expose a response body as a publisher instead of materializing it immediately:

```java
HttpResponse<Flow.Publisher<List<ByteBuffer>>> response =
        client.send(request, HttpResponse.BodyHandlers.ofPublisher());

Flow.Publisher<List<ByteBuffer>> body = response.body();
body.subscribe(mySubscriber);
```

The publisher returned by `ofPublisher()` permits **one subscription**. That subscriber must request data through completion/error, or cancel when it will not continue. Failing to subscribe, request, or cancel can leave the body unfinished and keep the HTTP exchange/resources alive.

Flow brings demand and cancellation to the body transport boundary; it does not define the business backpressure strategy of an entire application. Deeper reactive/concurrency composition belongs to the corresponding curriculum.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="http-client-policies">Version, Redirect, Timeout, Proxy, Authentication, and Cookies</a>

<details>
<summary>Click for details</summary>

HTTP settings do not all live at the same scope. Putting each policy at the correct boundary avoids treating shared client configuration as though it were per-request.

| Concern | Primary JDK API boundary | Meaning |
|---|---|---|
| Preferred HTTP version | `HttpClient.Builder.version(...)`, with a preferred version also available on a request | Expresses HTTP/1.1 or HTTP/2 preference; actual use still depends on the peer/network |
| Redirect | `HttpClient.Builder.followRedirects(...)` | Client policy `NEVER`, `NORMAL`, or `ALWAYS` |
| Connect timeout | `HttpClient.Builder.connectTimeout(...)` | Bounds connection establishment |
| Request timeout | `HttpRequest.Builder.timeout(...)` | Timeout attached to a specific request |
| Proxy | `HttpClient.Builder.proxy(...)` | Client-level `ProxySelector` |
| Authentication | `HttpClient.Builder.authenticator(...)` | Client-level `Authenticator` for HTTP authentication challenges |
| Cookies | `HttpClient.Builder.cookieHandler(...)` | `CookieHandler` shared by the client's requests |
| Async executor | `HttpClient.Builder.executor(...)` | Executor for asynchronous/dependent client work; when omitted, the implementation can still use an internal default executor |

The request owns its URI, method, headers, and body. Client policies form a shared baseline; a request represents one exchange.

These options describe **JDK HTTP mechanics**. Integration decisions such as retry rules by method/status, circuit breakers, service discovery, OAuth flows, domain error mapping, or choosing among HTTP client frameworks belong to the appropriate integration/security modules.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="http-client-resource-sharing">Connection Reuse and Resource Sharing</a>

<details>
<summary>Click for details</summary>

An `HttpClient` is more than an object with a `send` method. The Oracle API describes it as providing **configuration and resource sharing** for requests sent through it, and the default implementation typically manages a connection pool that may reuse connections when appropriate.

That leads to two ownership rules:

1. Reuse a client at a scope that matches requests sharing its configuration instead of constructing a client for every call.
2. If several components share one client, they also share its underlying lifecycle; a component should not close a client that it does not own.

Connection reuse is implementation/resource behavior, not an API guarantee that request B uses the exact socket from request A. Protocol version, destination, response-body completion, peer behavior, and transport conditions all affect reuse.

An unfinished response body can keep an exchange/connection occupied. Materializing handlers such as `ofString()` normally complete body production before exposing the materialized body value, while streaming handlers transfer continued consumption/close responsibility to the caller.

```text
shared HttpClient
   ├── shared configuration
   ├── managed connections/resources
   └── many HttpRequest exchanges
          ↓
   body lifetime must be completed
   so resources can be released/reused appropriately
```

Do not build application correctness around connection-pool implementation details. Reuse is a client resource optimization; correctness comes from HTTP semantics and the application's contract.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="http-client-lifecycle">Shutdown, Close, and Response-Body Lifetime</a>

<details>
<summary>Click for details</summary>

Java 21 added an explicit lifecycle API to `HttpClient`: it implements `AutoCloseable` and exposes `shutdown()`, `shutdownNow()`, `awaitTermination(...)`, `isTerminated()`, and `close()`.

There is an important API-contract boundary here. The **JDK built-in client** returned by `newHttpClient()` / `newBuilder().build()` overrides these methods with best-effort shutdown behavior. The base `HttpClient` class also provides default implementations, but those defaults do not by themselves guarantee that an arbitrary custom subclass owns real shutdown mechanics. The behavior below describes the JDK built-in implementation that normal application code receives from the factories.

- `shutdown()` starts an orderly shutdown: previously submitted requests may run to completion, new requests are no longer accepted, and the method does not wait for termination.
- `awaitTermination(duration)` waits after shutdown until operations complete, the duration expires, or the thread is interrupted.
- `shutdownNow()` attempts to begin immediate shutdown, but stopping active operations is best effort.
- `close()` begins orderly shutdown **and waits** until operations have completed and the client terminates.

```java
HttpClient client = HttpClient.newHttpClient();
try (client) {
    HttpResponse<String> response =
            client.send(request, BodyHandlers.ofString());
}
```

The **response-body lifetime** contributes to request lifetime. With `BodyHandlers.ofInputStream()`, the response can become available after headers while the body remains unread; the caller must read to EOF or close the stream:

```java
HttpResponse<InputStream> response =
        client.send(request, BodyHandlers.ofInputStream());

try (InputStream body = response.body()) {
    consume(body);
}
```

Closing the stream before exhaustion can close the underlying HTTP connection and prevent later reuse, but it still releases the exchange resource. `ofLines()` similarly requires exhausting or closing the `Stream`; `ofPublisher()` requires subscribing and requesting through completion/error, or cancelling the subscription.

The Java 21 API warns that unread/unclosed returned streams, or custom subscribers that neither request nor cancel, can stall orderly shutdown. Because `close()` waits for orderly termination, an abandoned streaming body can make close wait for a long time. **Body ownership is therefore part of correct client shutdown**, not merely memory cleanup.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="http-integration-boundary">The Boundary Between JDK HTTP Client and HTTP Integration</a>

<details>
<summary>Click for details</summary>

The Networking module owns **how the JDK HTTP Client API behaves**: client/request/response abstractions, body streaming, synchronous/asynchronous completion, policy configuration, connection/resource lifetime, and Java 21 shutdown semantics.

It does not own every HTTP integration concern in an application. When the question becomes one of these decisions:

- which calls service A should retry and with what backoff;
- whether to choose JDK `HttpClient`, Spring `RestClient`/`WebClient`, Feign, or another framework;
- how HTTP status/body becomes a domain error;
- where circuit breakers, bulkheads, rate limits, or observability policies live;
- how OAuth/token refresh/service credentials are organized;
- how API contracts/versioning and cross-service request orchestration are designed;

the learning path hands off to the appropriate integration, security, or architecture owner.

That boundary lets learners use the JDK API correctly without confusing a transport/client mechanism with an entire integration architecture:

```text
Networking
    JDK HttpClient mechanics
          ↓ consumed by
HTTP Integration
    application/client patterns + resilience + domain policy
```

The boundary also prevents leakage in the other direction: a framework HTTP client ultimately relies on networking/resource mechanics somewhere, but learning every integration pattern is not a prerequisite for understanding the JDK's `HttpClient`.

The next chapter keeps the same `java.net.http` family but changes the communication model: instead of independent request/response exchanges, the JDK WebSocket client manages a long-lived bidirectional message-oriented connection with listener demand, fragmentation, send completion, and close/error lifecycle.

</details>

- [Quay lại đầu trang](#back-to-top)
