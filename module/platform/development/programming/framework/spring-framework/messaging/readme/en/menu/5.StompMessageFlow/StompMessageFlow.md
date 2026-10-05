<a id="back-to-top"></a>

# Spring STOMP Message Flow

## Menu
- [1. Enabling the WebSocket message broker with @EnableWebSocketMessageBroker](#enable-websocket-message-broker)
- [2. WebSocketMessageBrokerConfigurer as the configuration extension point](#websocket-message-broker-configurer)
- [3. Registering STOMP endpoints](#register-stomp-endpoints)
- [4. Application and broker destination prefixes](#destination-prefixes)
- [5. clientInboundChannel](#client-inbound-channel)
- [6. clientOutboundChannel](#client-outbound-channel)
- [7. brokerChannel](#broker-channel)
- [8. Application-handler message flow](#application-message-flow)
- [9. Broker-directed message flow](#broker-message-flow)

## <a id="enable-websocket-message-broker">1. Enabling the WebSocket message broker with @EnableWebSocketMessageBroker</a>

<details>
<summary>Click for details</summary>

@EnableWebSocketMessageBroker turns on Spring's broker-backed messaging infrastructure for higher-level protocols such as STOMP over WebSocket. It imports the configuration that creates the message channels, annotation-based message handler, broker-side components, WebSocket sub-protocol handling, and supporting infrastructure needed for the server-side flow.

The annotation is therefore more than an endpoint switch. It establishes the internal message graph that later sections name explicitly: clientInboundChannel carries messages decoded from clients, brokerChannel carries server-side messages toward the broker, and clientOutboundChannel carries broker/application output toward connected clients.

Use it on a @Configuration class and implement WebSocketMessageBrokerConfigurer when the defaults need to be customized. The annotation does not decide application destinations or broker topology by itself; those choices come from the configurer.

</details>

- [Back to top](#back-to-top)

---

## <a id="websocket-message-broker-configurer">2. WebSocketMessageBrokerConfigurer as the configuration extension point</a>

<details>
<summary>Click for details</summary>

WebSocketMessageBrokerConfigurer is the main Java configuration extension point around @EnableWebSocketMessageBroker. Its callbacks let an application register STOMP endpoints, configure the application/broker routing model, tune inbound and outbound channels, tune WebSocket transport limits, add custom argument or return-value handlers, and adjust message conversion.

    @Configuration
    @EnableWebSocketMessageBroker
    class MessagingConfig implements WebSocketMessageBrokerConfigurer {
        @Override
        public void registerStompEndpoints(StompEndpointRegistry registry) {
            registry.addEndpoint("/ws");
        }

        @Override
        public void configureMessageBroker(MessageBrokerRegistry registry) {
            registry.setApplicationDestinationPrefixes("/app");
            registry.enableSimpleBroker("/topic");
        }
    }

Keep configuration at the layer it controls. Endpoint registration configures the WebSocket/STOMP entry point; message-broker configuration controls destination routing; channel callbacks control execution characteristics. Mixing those responsibilities makes message-flow failures harder to diagnose.

</details>

- [Back to top](#back-to-top)

---

## <a id="register-stomp-endpoints">3. Registering STOMP endpoints</a>

<details>
<summary>Click for details</summary>

A STOMP endpoint is the HTTP/WebSocket handshake URL that a client connects to before any STOMP frame can flow. For example, registry.addEndpoint("/ws") exposes /ws as the connection entry point. It is not the same thing as an application destination such as /app/orders or a broker destination such as /topic/orders.

Endpoint registration can add HandshakeInterceptor instances, configure allowed origins, and enable SockJS fallback:

    registry.addEndpoint("/ws")
            .setAllowedOrigins("https://app.example.test")
            .withSockJS();

Once the WebSocket connection is established, STOMP frames carry their own destination headers. Keeping endpoint URL and STOMP destination separate is essential when debugging: failure to connect belongs to the handshake/transport layer; failure to route an already connected SEND or SUBSCRIBE belongs to STOMP/Spring messaging.

</details>

- [Back to top](#back-to-top)

---

## <a id="destination-prefixes">4. Application and broker destination prefixes</a>

<details>
<summary>Click for details</summary>

Destination prefixes decide which server-side component should receive a message. An application prefix such as /app marks messages intended for annotated application handlers. Before mapping to @MessageMapping, Spring removes that prefix, so a SEND to /app/orders may match @MessageMapping("/orders").

Broker prefixes such as /topic and /queue identify destinations handled by the configured message broker. With the built-in simple broker, those prefix names are routing conventions; /topic and /queue do not magically create vendor-grade topic or queue semantics. With an external broker relay, the broker's own destination model becomes relevant.

    registry.setApplicationDestinationPrefixes("/app");
    registry.enableSimpleBroker("/topic", "/queue");

Choose disjoint, readable prefixes. Overlapping or ambiguous conventions make it difficult to tell whether a frame should reach application code or bypass it and go directly to the broker.

</details>

- [Back to top](#back-to-top)

---

## <a id="client-inbound-channel">5. clientInboundChannel</a>

<details>
<summary>Click for details</summary>

clientInboundChannel is the application-side entry path for messages received from connected STOMP clients after WebSocket transport decoding. CONNECT, SUBSCRIBE, SEND, DISCONNECT, and related events become Spring Messages and enter this channel for further processing.

Multiple infrastructure handlers can subscribe to the channel. Depending on the message type and destination, an inbound message may be handled by the annotated-method infrastructure, the message broker, user-destination handling, or other framework components.

The channel is executor-backed in the broker configuration, so inbound handling can cross a thread boundary. That means application code should not assume the WebSocket I/O thread is the thread that invokes @MessageMapping. Channel executor sizing, ordering, and interception become operational concerns and are covered later in the module.

</details>

- [Back to top](#back-to-top)

---

## <a id="client-outbound-channel">6. clientOutboundChannel</a>

<details>
<summary>Click for details</summary>

clientOutboundChannel carries messages that are ready to be delivered to WebSocket clients. Broker output, resolved user-destination messages, and application replies ultimately use this outbound path before Spring encodes them as STOMP frames and writes them to the correct WebSocket sessions.

The channel separates message production from socket I/O. A broker component can publish a Spring Message without directly manipulating a WebSocketSession; the sub-protocol WebSocket layer uses session metadata in the message headers to reach the correct client connection.

Like the inbound channel, clientOutboundChannel is executor-backed by default in the broker setup. Concurrent execution can improve throughput, but it means publication order requires explicit attention when the application depends on strict ordering. Spring provides preserve-order options discussed in the lifecycle/ordering chapter.

</details>

- [Back to top](#back-to-top)

---

## <a id="broker-channel">7. brokerChannel</a>

<details>
<summary>Click for details</summary>

brokerChannel is the path for messages produced on the server side that should be sent to the configured message broker. A @MessageMapping method's return value, an @SendTo/@SendToUser result after resolution, or an application component using SimpMessagingTemplate can ultimately produce a message on this path.

This channel is conceptually different from clientInboundChannel. The inbound channel represents traffic arriving from remote clients. brokerChannel represents application-originated or framework-resolved messages moving toward the broker for subscription matching and distribution.

That distinction is useful when tracing a failure: if a controller ran and produced a valid reply but no subscriber receives it, inspect the broker-bound destination and outbound path. If the controller never ran, start earlier at clientInboundChannel and application destination mapping.

</details>

- [Back to top](#back-to-top)

---

## <a id="application-message-flow">8. Application-handler message flow</a>

<details>
<summary>Click for details</summary>

Consider a client that sends a STOMP SEND frame to /app/greeting. The WebSocket layer decodes the frame and Spring creates a Message whose destination is /app/greeting. That message enters clientInboundChannel.

Because /app is configured as an application destination prefix, Spring routes the message to the annotated handler infrastructure rather than directly to the broker. The prefix is removed for matching, so @MessageMapping("/greeting") can handle it. Payload conversion and argument resolution happen before the method is invoked.

If the method returns a value, Spring converts that value to an outbound Message and chooses a reply destination. By default the broker destination is derived from the inbound destination; @SendTo and @SendToUser can override that decision. The resulting message is sent through brokerChannel, the broker finds matching subscriptions, and delivery continues through clientOutboundChannel to the appropriate WebSocket sessions.

</details>

- [Back to top](#back-to-top)

---

## <a id="broker-message-flow">9. Broker-directed message flow</a>

<details>
<summary>Click for details</summary>

Some client messages are broker-directed from the start. A SUBSCRIBE to /topic/greeting enters clientInboundChannel and is handled by the broker, which records the subscription. A SEND whose destination belongs to a configured broker prefix can likewise bypass annotated application handlers and go directly to broker processing.

When the broker later receives a message for /topic/greeting, it matches current subscriptions and creates outbound messages for the relevant client sessions. Those messages pass through clientOutboundChannel, are encoded as STOMP MESSAGE frames, and are written over WebSocket.

This flow explains why destination prefixes are architecture, not decoration. /app expresses "application code must process this first"; /topic or /queue usually expresses "the broker owns this destination." If an application accidentally sends a business command directly to a broker prefix, no @MessageMapping method will validate or execute that command.

</details>

- [Back to top](#back-to-top)
