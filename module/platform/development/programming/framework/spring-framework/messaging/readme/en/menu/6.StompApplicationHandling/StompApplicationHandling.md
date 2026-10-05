<a id="back-to-top"></a>

# STOMP Application Message Handling

## Menu
- [1. @MessageMapping handler methods](#message-mapping)
- [2. Destination variables](#destination-variable)
- [3. Payload conversion](#payload-conversion)
- [4. Payload validation](#payload-validation)
- [5. @SubscribeMapping](#subscribe-mapping)
- [6. @SendTo and reply destinations](#send-to)
- [7. @SendToUser and user replies](#send-to-user)
- [8. SimpMessagingTemplate](#simp-messaging-template)
- [9. Message exception handling](#message-exception-handling)

## <a id="message-mapping">1. @MessageMapping handler methods</a>

<details>
<summary>Click for details</summary>

@MessageMapping is the STOMP application-programming equivalent of destination-based dispatch. Spring examines the destination of a Message arriving on clientInboundChannel, removes the configured application prefix, and selects a controller method whose mapping pattern matches the remaining destination.

Mappings can be declared at class and method level. Handler parameters can request the complete Message, MessageHeaders, typed header accessors, individual @Header values, @DestinationVariable values, the payload, or the connection Principal. The payload parameter is implicit when no other argument resolver matches.

    @Controller
    class OrderMessageController {
        @MessageMapping("/orders")
        @SendTo("/topic/orders")
        OrderView create(OrderCommand command) {
            return service.create(command);
        }
    }

Keep destination handlers focused on application work. Broker subscription matching, WebSocket session I/O, and protocol framing remain infrastructure responsibilities outside the controller method.

</details>

- [Back to top](#back-to-top)

---

## <a id="destination-variable">2. Destination variables</a>

<details>
<summary>Click for details</summary>

Message destinations can contain template variables, which let one handler represent a family of logical routes. @DestinationVariable binds the value extracted from the matched destination to a method argument.

    @MessageMapping("/rooms/{roomId}/messages")
    void post(@DestinationVariable String roomId, ChatMessage message) {
        chatService.post(roomId, message);
    }

The variable comes from the message destination, not from the payload. This makes it useful for routing context that is part of the address itself. Keep the destination convention stable and avoid duplicating the same identifier in both destination and payload unless the application has a clear validation reason to compare them.

Destination patterns are routing syntax, not authorization rules. A caller being able to name a room or user in a destination does not establish permission to act on it; security policy belongs to Spring Security.

</details>

- [Back to top](#back-to-top)

---

## <a id="payload-conversion">3. Payload conversion</a>

<details>
<summary>Click for details</summary>

Annotated handlers normally work with application types rather than raw byte arrays. Before invocation, Spring uses the configured MessageConverter chain to turn the incoming payload into the handler parameter type. On the return path, a converter serializes the returned value into an outbound message representation.

For example, a STOMP SEND carrying JSON with an application/json content type can be converted to OrderCommand when an appropriate JSON converter is configured. If the content type, payload representation, and target type do not match any converter, handler invocation cannot proceed.

Conversion belongs between routing and application logic: first Spring finds the handler from the destination, then it resolves and converts arguments, then it invokes the method. This distinction helps diagnose failures. A destination mismatch is not a JSON problem, and a conversion exception is not evidence that the broker failed.

WebSocketMessageBrokerConfigurer.configureMessageConverters(...) is the extension point when the default converter set needs to be supplemented or replaced.

</details>

- [Back to top](#back-to-top)

---

## <a id="payload-validation">4. Payload validation</a>

<details>
<summary>Click for details</summary>

Payload validation can run as part of annotated method argument resolution. Mark a payload parameter with Jakarta @Valid or Spring @Validated and ensure an appropriate Validator is configured; Spring validates the converted object before calling the handler.

    @MessageMapping("/orders")
    void create(@Valid OrderCommand command) {
        orderService.create(command);
    }

Validation belongs after conversion because constraints apply to the Java object, not to an unparsed STOMP frame. A validation failure prevents normal handler execution and can be handled through the messaging exception-handling path.

Keep validation responsibilities separated. This chapter explains how messaging invokes validation. Constraint design, custom Validator mechanics, and Bean Validation depth belong to the validation/data-binding curriculum. Security checks should likewise not be disguised as validation annotations.

</details>

- [Back to top](#back-to-top)

---

## <a id="subscribe-mapping">5. @SubscribeMapping</a>

<details>
<summary>Click for details</summary>

@SubscribeMapping handles STOMP SUBSCRIBE messages that are routed to application code. It is useful when subscribing should immediately produce application-generated data, such as an initial snapshot, without requiring a separate SEND request.

The important return-value rule differs from ordinary @MessageMapping: when an @SubscribeMapping method has no @SendTo or @SendToUser, Spring sends the return value directly back to the connected client and does not route that reply through the broker. This creates a simple request-reply pattern tied to the subscription event.

    @SubscribeMapping("/prices/initial")
    PriceBook initialPrices() {
        return priceService.snapshot();
    }

Use @SubscribeMapping for subscription-time application behavior, not as a replacement for normal broker subscriptions. If continuing updates should be broadcast through a broker destination, model that destination and subscription explicitly.

</details>

- [Back to top](#back-to-top)

---

## <a id="send-to">6. @SendTo and reply destinations</a>

<details>
<summary>Click for details</summary>

@SendTo customizes where the return value of a message-handling method is sent. Spring converts the return value into a Message and sends it toward the broker destination or destinations declared by the annotation.

    @MessageMapping("/orders")
    @SendTo("/topic/orders")
    OrderView create(OrderCommand command) {
        return service.create(command);
    }

Without an explicit @SendTo on a normal @MessageMapping method, Spring can derive a default broker destination from the inbound destination according to the configured message-handling conventions. Declaring @SendTo is useful when the reply destination is part of the application contract or differs from that default.

@SendTo controls reply routing; it does not publish arbitrary messages at arbitrary times. For notifications triggered outside the handler return path, use SimpMessagingTemplate.

</details>

- [Back to top](#back-to-top)

---

## <a id="send-to-user">7. @SendToUser and user replies</a>

<details>
<summary>Click for details</summary>

@SendToUser routes a handler return value through Spring's user-destination mechanism. Instead of broadcasting the same broker destination to every subscriber, Spring associates the message with the user from the inbound message and resolves it to destination(s) for that user's active session or sessions.

    @MessageExceptionHandler
    @SendToUser("/queue/errors")
    ErrorView handle(OrderRejectedException ex) {
        return new ErrorView(ex.getMessage());
    }

The default user-destination prefix is /user/. Clients subscribe using the user-destination convention while Spring translates it into session-specific broker destinations. Chapter 7 covers that resolution in depth.

Use @SendToUser for user-targeted replies, not for authorization. The Principal and destination resolution identify *where* to send; Spring Security remains responsible for deciding what a user is allowed to do.

</details>

- [Back to top](#back-to-top)

---

## <a id="simp-messaging-template">8. SimpMessagingTemplate</a>

<details>
<summary>Click for details</summary>

SimpMessagingTemplate lets application code send messages programmatically instead of tying every publication to a controller return value. It is typically injected into a service or controller and backed by the broker-facing MessageChannel created by the WebSocket message-broker configuration.

    @Service
    class PricePublisher {
        private final SimpMessagingTemplate messaging;

        PricePublisher(SimpMessagingTemplate messaging) {
            this.messaging = messaging;
        }

        void publish(PriceUpdate update) {
            messaging.convertAndSend("/topic/prices", update);
        }
    }

convertAndSend(...) converts an application object before sending. convertAndSendToUser(...) targets Spring's user-destination mechanism. The template also exposes send timeout and header customization.

Prefer the template when publication originates from timers, HTTP handlers, domain services, or other application events. For a simple request-reply from @MessageMapping, returning a value is usually clearer.

</details>

- [Back to top](#back-to-top)

---

## <a id="message-exception-handling">9. Message exception handling</a>

<details>
<summary>Click for details</summary>

Exceptions from annotated message handlers can be handled with @MessageExceptionHandler. A handler can declare the exception type through the annotation or by accepting the exception as a method argument. The exception method supports the same general messaging argument and return-value model as @MessageMapping.

    @MessageExceptionHandler(OrderRejectedException.class)
    @SendToUser("/queue/errors")
    ErrorView rejected(OrderRejectedException ex) {
        return new ErrorView(ex.getMessage());
    }

By default, an @MessageExceptionHandler method applies within its controller class hierarchy. To apply common messaging exception handling across controllers, place such methods in @ControllerAdvice.

Separate failure layers when designing responses. Application exceptions can become structured application messages. Conversion or validation failures may also enter handler-exception processing when raised in that pipeline. WebSocket transport failures and broker connection failures are different concerns and should not be hidden behind an application-level error payload.

</details>

- [Back to top](#back-to-top)
