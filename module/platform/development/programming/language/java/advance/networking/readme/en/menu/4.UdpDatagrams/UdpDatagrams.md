<a id="back-to-top"></a>

# UDP and Datagram Communication

## Menu
- [The Datagram Model and DatagramSocket](#udp-datagram-model)
- [DatagramPacket and Per-Packet Addressing](#datagram-packet-addressing)
- [Ordering, Delivery, and Reliability Limits](#udp-delivery-semantics)
- [What DatagramSocket.connect Means](#datagram-connect-semantics)
- [Broadcast and Multicast](#udp-broadcast-multicast)
- [DatagramChannel and the NIO Boundary](#datagram-channel-boundary)

## <a id="udp-datagram-model">The Datagram Model and DatagramSocket</a>

<details>
<summary>Click for details</summary>

UDP uses a **datagram** model: each send produces an independent unit of data with its own payload and destination endpoint. Java represents a UDP socket with `DatagramSocket` and the unit being sent or received with `DatagramPacket`.

Unlike TCP, the receiver does not call `listen()`/`accept()` to create a new connection for each peer. A `DatagramSocket` normally binds to one local endpoint and receives many datagrams through that same socket:

```text
sender A ---- datagram ----\
                            > DatagramSocket bound to local port
sender B ---- datagram ----/
```

A minimal receiver looks like this:

```java
try (DatagramSocket socket = new DatagramSocket(9000)) {
    byte[] buffer = new byte[2048];
    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);

    socket.receive(packet);

    String text = new String(
            packet.getData(),
            packet.getOffset(),
            packet.getLength(),
            StandardCharsets.UTF_8);
}
```

On a normal blocking `DatagramSocket`, `receive()` waits until a datagram arrives or the operation ends because of timeout, error, or close. Each receive handles at most one datagram; Java does not combine several datagrams into a TCP-style byte stream.

UDP is useful when an application wants message boundaries and is prepared to define its own handling of loss, ordering, retries, or stale data. Those policies belong to the application protocol; `DatagramSocket` provides the datagram transport mechanism itself.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="datagram-packet-addressing">DatagramPacket and Per-Packet Addressing</a>

<details>
<summary>Click for details</summary>

A `DatagramPacket` carries both a **data region** and a **peer address**. The address means the destination when sending and the source after receiving.

For a send, the packet contains the destination:

```java
byte[] data = "PING".getBytes(StandardCharsets.UTF_8);
InetSocketAddress target = new InetSocketAddress("127.0.0.1", 9000);

DatagramPacket packet = new DatagramPacket(data, data.length, target);
socket.send(packet);
```

For a receive, `receive(packet)` fills the buffer and updates the packet with the sender's address and port. An unconnected socket can therefore reply to the peer associated with each individual datagram:

```java
byte[] buffer = new byte[2048];
DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
socket.receive(packet);

SocketAddress sender = packet.getSocketAddress();
int receivedLength = packet.getLength();

DatagramPacket reply = new DatagramPacket(
        packet.getData(),
        packet.getOffset(),
        receivedLength,
        sender);
socket.send(reply);
```

`getOffset()` and `getLength()` identify the valid data region in the backing array. Do not process the whole array returned by `getData()` as though every byte belongs to the current message.

The receive buffer also limits the datagram. If an incoming message is longer than the current `DatagramPacket` length, Java keeps only the part that fits and **truncates the remainder** for that receive. UDP does not turn the remainder into a later `receive()` call. The buffer size must therefore match the protocol's expected datagram size.

After a receive, `getLength()` reports the size of the message just received. If application code explicitly calls `setLength(...)`, that value controls how much of the buffer is available for a later receive, so change it only when the protocol or buffer-management policy actually requires it.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="udp-delivery-semantics">Ordering, Delivery, and Reliability Limits</a>

<details>
<summary>Click for details</summary>

UDP preserves the **datagram boundary** at the API: one send corresponds to one datagram, and one receive obtains one datagram (or its leading portion if the buffer is too small). That boundary does not imply reliable delivery.

`DatagramSocket` does not add TCP-like guarantees for the application:

- a datagram may fail to arrive;
- datagrams can arrive in a different order from the send order;
- an application should not depend on each logical message appearing exactly once if its protocol requires stronger semantics;
- the sender does not receive a stream-level acknowledgment from `DatagramSocket` proving that the peer application processed the message.

If a use case needs sequence numbers, acknowledgments, retries, duplicate detection, expiration, or application-level reassembly, the application protocol must define those rules. The JDK UDP API does not turn UDP into a reliable session automatically.

For example, an independent telemetry sample may tolerate occasional loss and favor simple low-overhead delivery. A workflow where every command must be processed in strict order needs additional protocol semantics or a transport with more suitable guarantees.

Keep this contrast with TCP clear:

```text
TCP -> ordered byte stream; the application defines message framing
UDP -> datagram boundaries are present; the application adds reliability/order if required
```

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="datagram-connect-semantics">What DatagramSocket.connect Means</a>

<details>
<summary>Click for details</summary>

The name `DatagramSocket.connect(...)` is easy to misread through a TCP mental model. For UDP, `connect` **does not perform a TCP-style handshake and does not establish a reliable session**. It associates the socket with one remote IP address and port.

After connecting:

- the socket has a default remote peer;
- for an ordinary unicast peer, datagrams received through the socket are filtered to that associated peer;
- `send()` must target the matching peer; a packet carrying a different address is rejected;
- code can query the remote endpoint through methods such as `getRemoteSocketAddress()` and use a simpler peer-specific flow.

A multicast address is a special case: a datagram socket connected to a multicast address is used only for sending and does not provide the same receive-filter model.

```java
try (DatagramSocket socket = new DatagramSocket()) {
    InetSocketAddress peer = new InetSocketAddress("127.0.0.1", 9000);
    socket.connect(peer);

    byte[] data = "PING".getBytes(StandardCharsets.UTF_8);
    DatagramPacket packet = new DatagramPacket(data, data.length, peer);
    socket.send(packet); // target must match the connected peer
}
```

`connect()` does not require the peer to exist or to "accept" anything. Whether a datagram arrives and whether a peer application processes it still follows UDP semantics. On a connected datagram socket, an unreachable destination **may** surface as `PortUnreachableException`, but the Java API does not guarantee that the exception will always be reported.

When `disconnect()` succeeds, it removes the remote association and returns the socket to unconnected use, where each packet can identify its own peer. Java 21 also permits `disconnect()` to throw `UncheckedIOException` if disconnection fails; after such a failure the socket may be left in an unspecified state, so closing it is safer than assuming it can be reused. Use UDP `connect()` when one socket primarily communicates with one peer and peer filtering/default addressing is useful, rather than because the application needs a TCP connection lifecycle.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="udp-broadcast-multicast">Broadcast and Multicast</a>

<details>
<summary>Click for details</summary>

Broadcast and multicast both send datagrams toward multiple potential receivers, but they use different addressing models.

**Broadcast** uses an IPv4 broadcast address. `DatagramSocket` exposes the `SO_BROADCAST` socket option; where possible, a newly constructed datagram socket has this enabled so broadcast datagrams can be sent. The Java API recommends that a receiver expecting broadcasts bind to the wildcard address because receiving broadcasts while bound to a specific address can be implementation dependent.

```java
try (DatagramSocket socket = new DatagramSocket()) {
    socket.setBroadcast(true);

    byte[] data = "DISCOVER".getBytes(StandardCharsets.UTF_8);
    DatagramPacket packet = new DatagramPacket(
            data,
            data.length,
            new InetSocketAddress("255.255.255.255", 9000));
    socket.send(packet);
}
```

The usable broadcast address normally depends on the network/subnet; the address above is only an API illustration.

**Multicast** sends to a multicast group. Java provides `MulticastSocket`, a `DatagramSocket` subclass, for joining/leaving groups and choosing the relevant network interface. On Java 21, prefer `joinGroup(SocketAddress, NetworkInterface)` because it makes the local interface explicit; the older overload that accepts only `InetAddress` is deprecated.

```java
NetworkInterface netIf = NetworkInterface.getByName("eth0");
InetSocketAddress group = new InetSocketAddress("239.10.10.10", 9000);

try (MulticastSocket socket = new MulticastSocket(9000)) {
    socket.joinGroup(group, netIf);
    try {
        byte[] buffer = new byte[2048];
        DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
        socket.receive(packet);
    } finally {
        socket.leaveGroup(group, netIf);
    }
}
```

Multicast also involves the outgoing network interface and TTL/hop scope. Those are socket/networking mechanics owned here; designing a discovery protocol, membership strategy, or wider multicast topology belongs to the appropriate protocol/system layer.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="datagram-channel-boundary">DatagramChannel and the NIO Boundary</a>

<details>
<summary>Click for details</summary>

`DatagramChannel` is the NIO counterpart of `DatagramSocket`. It still sends and receives **datagrams**, but uses `ByteBuffer` and the channel model instead of making `DatagramPacket` the primary abstraction.

An unconnected channel uses `send`/`receive` with a per-datagram address:

```java
try (DatagramChannel channel = DatagramChannel.open()) {
    channel.bind(new InetSocketAddress(9000));

    ByteBuffer buffer = ByteBuffer.allocate(2048);
    SocketAddress sender = channel.receive(buffer);

    if (sender != null) {
        buffer.flip();
        channel.send(buffer, sender);
    }
}
```

After `connect(remote)`, a `DatagramChannel` remains associated with that peer until `disconnect()` or `close()`. `read`/`write` can then operate against the connected peer; the channel is still a datagram transport rather than a TCP stream.

The important boundary with NIO is the execution model:

- in blocking mode, `receive()` can wait until a datagram becomes available;
- in non-blocking mode, `receive()` returns `null` immediately when no datagram is ready;
- because it is a `SelectableChannel`, `DatagramChannel` can register with a `Selector` in a non-blocking workflow;
- multicast membership also has channel APIs through `join(...)` and `MembershipKey`.

This chapter only places `DatagramChannel` in the correct UDP mental model. The next chapter first establishes timeout, failure, cancellation, and resource-lifetime control that applies across socket styles; the later NIO Networking chapter then develops `ByteBuffer` state, selector readiness, registration/interest sets, and the broader channel execution models.

</details>

- [Quay lại đầu trang](#back-to-top)
