<a id="back-to-top"></a>

# Addressing, Name Resolution, and Endpoints

## Menu
- [InetAddress and the IP Address Model](#inet-address-model)
- [Name Resolution, Failure, and Caching](#name-resolution)
- [InetAddress Resolver SPI and Custom Resolvers](#name-resolution-spi)
- [NetworkInterface and Local Addresses](#network-interface-model)
- [SocketAddress and InetSocketAddress](#socket-address-model)
- [URI, URL, and Resource Identity](#uri-url-resource-identity)
- [URLConnection and the Resource-Access Boundary](#urlconnection-boundary)
- [UnixDomainSocketAddress and Non-IP Endpoints](#unix-domain-addresses)

## <a id="inet-address-model">InetAddress and the IP Address Model</a>

<details>
<summary>Click for details</summary>

InetAddress is the JDK abstraction for an **IP address**. An instance represents either a 32-bit IPv4 or 128-bit IPv6 address and may also carry the corresponding host name. Most application code should work through InetAddress instead of branching directly on Inet4Address or Inet6Address.

For example:

~~~java
InetAddress address = InetAddress.getByName("example.com");

System.out.println(address.getHostAddress());
System.out.println(address.getHostName());
~~~

A host name and an IP address are not necessarily one-to-one. One host can resolve to multiple addresses, so getAllByName(...) can be more representative than getByName(...):

~~~java
for (InetAddress address : InetAddress.getAllByName("example.com")) {
    System.out.println(address.getHostAddress());
}
~~~

InetAddress does not include a port. When transport addressing is needed, Java combines host/IP information with a port in InetSocketAddress.

Do not treat isReachable() as an absolute service-health check. Reachability depends on network configuration, firewall policy, and implementation behavior. Application health is usually better observed through the application protocol itself.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="name-resolution">Name Resolution, Failure, and Caching</a>

<details>
<summary>Click for details</summary>

When code uses a host name, the system has to resolve that name to one or more IP addresses. InetAddress.getByName(...) can trigger name resolution and may fail with UnknownHostException.

Resolution is fallible system I/O, not a pure string transformation:

~~~text
host name
   ↓
resolver used by the JVM/platform
   ↓
one or more IP addresses
~~~

The JDK caches successful and failed lookups according to relevant networking/security configuration. A DNS change outside the process therefore does not imply that an already-running JVM immediately observes a new result.

A practical distinction is that the core InetAddress lookup methods do not accept a per-call timeout. Do not assume a socket connect timeout also bounds DNS resolution; they are different stages.

Classify failures by stage:

- resolution failure → commonly UnknownHostException;
- resolution succeeds but transport connection fails → ConnectException, timeout, or another socket error;
- multiple resolved addresses → the effective endpoint depends on which address is attempted.

When troubleshooting, recording both the original host name and the resolved address helps separate resolution failures from connection failures.

Resolution can also happen in the reverse direction. If an `InetAddress` was created only from an IP address, `getHostName()` may perform a reverse name lookup; `getCanonicalHostName()` is explicitly a best-effort fully qualified name lookup. This matters in logging and diagnostic paths because a method that appears to be simple formatting can cross the resolver boundary. When code only needs to preserve the host text of an `InetSocketAddress`, `getHostString()` avoids triggering a reverse lookup.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="name-resolution-spi">InetAddress Resolver SPI and Custom Resolvers</a>

<details>
<summary>Click for details</summary>

Modern Java exposes an **InetAddress Resolver SPI** in the java.net.spi package. It is an extension point for infrastructure or library code that needs to control how host names and addresses are resolved; ordinary applications rarely need to implement it.

The key abstractions are:

- InetAddressResolver, which performs host-to-address and reverse lookups;
- InetAddressResolverProvider, the service provider that supplies the JVM-wide resolver implementation.

Mental model:

~~~text
application
→ InetAddress API
→ service-loaded resolver provider
→ resolver
→ naming system
~~~

The provider participates in Java's service-provider mechanism, so applications using the ordinary InetAddress API can benefit from a custom resolver without replacing every lookup call.

The JVM maintains one system-wide resolver instance for `InetAddress`. After VM initialization, the first lookup causes `InetAddress` to locate providers with `ServiceLoader` through the system class loader; the first provider found supplies that resolver. If no provider is found, the built-in resolver is used. A custom resolver is therefore JVM-wide infrastructure, not a per-request resolver object selected independently by each caller.

Changing the resolver changes **how resolution happens**, not the application-level semantics: a lookup can still fail, can return several addresses, and remains a distinct stage before connect.

The repository has a Java Version module for the feature's Java 18 introduction. Networking owns the current conceptual/runtime view; Java Version owns the release-history view.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="network-interface-model">NetworkInterface and Local Addresses</a>

<details>
<summary>Click for details</summary>

NetworkInterface represents a network interface visible to the JVM: Ethernet, Wi-Fi, loopback, virtual interfaces, or other interfaces exposed by the operating system.

It matters when code needs to:

- enumerate local addresses;
- choose an interface for multicast;
- inspect loopback/up/down state;
- avoid assuming that one machine has exactly one IP address.

Example:

~~~java
for (NetworkInterface nic :
        Collections.list(NetworkInterface.getNetworkInterfaces())) {

    System.out.println(nic.getName());

    for (InetAddress address : Collections.list(nic.getInetAddresses())) {
        System.out.println("  " + address.getHostAddress());
    }
}
~~~

Developer machines, container hosts, and cloud servers commonly expose several interfaces. Selecting the “first address” is therefore not a reliable machine-identity strategy.

NetworkInterface belongs in Networking because it affects binding, multicast, and local-address selection. It is not a model for network topology, routing policy, or service discovery.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="socket-address-model">SocketAddress and InetSocketAddress</a>

<details>
<summary>Click for details</summary>

SocketAddress is the abstraction for an **address that a socket or channel can bind to or connect to**. Internet sockets commonly use InetSocketAddress.

~~~java
InetSocketAddress remote =
        new InetSocketAddress("example.com", 443);
~~~

An InetSocketAddress can be:

- **resolved**, meaning it already has an InetAddress;
- **unresolved**, meaning it keeps the host string and port without resolving yet.

InetSocketAddress.createUnresolved(...) is useful when another layer, such as proxy handling, needs the original host name.

The same address abstraction appears in two roles:

~~~java
server.bind(new InetSocketAddress("0.0.0.0", 8080));
client.connect(new InetSocketAddress("example.com", 443));
~~~

Binding to a wildcard local address means “listen on matching local interfaces”; it does not mean the remote peer is 0.0.0.0.

Keeping local and remote endpoints distinct makes later TCP and NIO lifecycle reasoning much easier: a socket/channel has a local endpoint and, after connect/accept, a corresponding remote endpoint.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="uri-url-resource-identity">URI, URL, and Resource Identity</a>

<details>
<summary>Click for details</summary>

URI and URL live near the networking APIs, but their main job is **resource identification**, not socket addressing.

A URI can have the shape:

~~~text
scheme://authority/path?query#fragment
~~~

For example:

~~~java
URI uri =
        URI.create("https://api.example.com/orders/42?detail=true");
~~~

The authority may eventually lead to a network endpoint, while the path and query express resource identity and protocol/application meaning above the socket layer.

URL is tied to protocol-handler based resource access and can open a URLConnection. URI is better suited to representing, validating, and manipulating identifiers. Modern APIs such as HttpRequest accept URI as the request target.

Practical rule:

- represent an identifier → prefer URI;
- represent a socket endpoint → SocketAddress / InetSocketAddress;
- perform modern HTTP client work → HttpClient + HttpRequest using a URI.

Avoid hand-parsing URLs with string slicing; the standard abstractions handle encoding, authorities, IPv6 literals, and relative-reference rules more safely.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="urlconnection-boundary">URLConnection and the Resource-Access Boundary</a>

<details>
<summary>Click for details</summary>

URLConnection is the older general abstraction for accessing a resource through a URL protocol handler. HttpURLConnection extends that model with HTTP-specific behavior.

Example:

~~~java
URLConnection connection =
        URI.create("https://example.com")
           .toURL()
           .openConnection();

connection.setConnectTimeout(3_000);
connection.setReadTimeout(3_000);
~~~

These are **URLConnection operation** timeouts, not a universal DNS timeout. One HttpURLConnection instance represents one request, while the underlying transport connection may be reused transparently by the implementation.

For new HTTP client code, java.net.http.HttpClient usually provides a clearer model because it separates reusable client configuration, requests, response-body handling, synchronous/asynchronous completion, and WebSocket creation.

This section exists because URLConnection is still part of the JDK networking model and appears in legacy code and library APIs. Modern HTTP mechanics are covered in the dedicated HttpClient chapter.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="unix-domain-addresses">UnixDomainSocketAddress and Non-IP Endpoints</a>

<details>
<summary>Click for details</summary>

Not every socket endpoint crosses an IP network. Java supports **Unix-domain sockets**, where an endpoint is identified by a path on supporting operating systems.

~~~java
UnixDomainSocketAddress address =
        UnixDomainSocketAddress.of("/tmp/my-service.sock");
~~~

UnixDomainSocketAddress is still a SocketAddress, so the bind/connect mental model remains useful even though the protocol family is not an Internet socket.

Unix-domain sockets are useful for communication between processes on the same machine:

- no TCP port allocation is required;
- the endpoint participates in a filesystem-like namespace;
- they fit daemon or local-service communication well.

In the JDK, support is primarily exposed through SocketChannel and ServerSocketChannel opened with the appropriate protocol family. Availability is platform-dependent, so portable code must handle unsupported environments.

The repository's Java Version area owns the Java 16 introduction history; Networking owns the current usage and mental model.

With names, addresses, and endpoints now separated clearly, the next chapter moves to TCP and follows a stream-oriented connection through bind/connect/accept, read/write, and close.

</details>

- [Quay lại đầu trang](#back-to-top)
