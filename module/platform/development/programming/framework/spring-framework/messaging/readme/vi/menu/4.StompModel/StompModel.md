<a id="back-to-top"></a>

# STOMP và Simple Messaging Model

## Menu
- [1. Vì sao dùng STOMP trên WebSocket](#stomp-purpose)
- [2. STOMP frame và command](#stomp-frames-commands)
- [3. Destination và subscription](#stomp-destinations-subscriptions)
- [4. Header và message content](#stomp-headers-content)
- [5. Heartbeat và receipt](#stomp-heartbeats-receipts)
- [6. Acknowledgement, transaction và ranh giới broker support](#stomp-ack-transactions)
- [7. STOMP trên WebSocket transport](#stomp-over-websocket)
- [8. SIMP message type cùng destination, session và user headers](#simp-message-model)
- [9. Ánh xạ STOMP frame thành Spring Message](#stomp-spring-message)

## <a id="stomp-purpose">1. Vì sao dùng STOMP trên WebSocket</a>

<details>
<summary>Xem chi tiết</summary>

WebSocket cho hai peer một full-duplex pipe nhưng không định nghĩa application message có ý nghĩa gì. Nếu mỗi application tự phát minh envelope, destination syntax, subscription model và error convention riêng, client và server sẽ coupling chặt vào private protocol đó.

STOMP bổ sung một messaging protocol hướng text ở trên transport như WebSocket hoặc TCP. Nó đưa vào các command như CONNECT, SEND, SUBSCRIBE, UNSUBSCRIBE, ACK, NACK và DISCONNECT; cùng header, destination, subscription, heartbeat và receipt. Spring dùng các semantics này để xây broker-backed application programming model trên generic Message infrastructure.

Vì vậy lý do chọn STOMP trên raw WebSocket không phải vì WebSocket không thể chở bytes. STOMP hữu ích khi application cần vocabulary messaging dùng chung: route tới destination, đăng ký subscription, broadcast qua broker và map application handler vào logical destination.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="stomp-frames-commands">2. STOMP frame và command</a>

<details>
<summary>Xem chi tiết</summary>

Một STOMP frame gồm command line, zero hoặc nhiều header, một blank line, optional body và null byte kết thúc. Command xác định protocol action còn header mang routing/protocol metadata.

Client-to-server command gồm CONNECT/STOMP, SEND, SUBSCRIBE, UNSUBSCRIBE, ACK, NACK, BEGIN, COMMIT, ABORT và DISCONNECT. Server-to-client command gồm CONNECTED, MESSAGE, RECEIPT và ERROR. StompDecoder/StompEncoder của Spring chuyển đổi giữa wire-level frame và Spring Message để mã ứng dụng thường không phải tự parse text frame.

Command nằm ở STOMP protocol layer, không map trực tiếp 1:1 thành Java controller method. Một SEND có thể trở thành application message được @MessageMapping xử lý, hoặc có thể route tới broker tùy destination prefix. SUBSCRIBE thường hướng broker nhưng cũng có thể map vào @SubscribeMapping để application trả initial reply.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="stomp-destinations-subscriptions">3. Destination và subscription</a>

<details>
<summary>Xem chi tiết</summary>

STOMP dùng destination string làm logical address. Protocol cố ý để semantics chính xác của destination cho server/broker quyết định. Tên như /topic/prices và /queue/orders chỉ là convention cho tới khi configured broker gán hành vi thực tế.

Client gửi SUBSCRIBE frame với destination và subscription id. Sau đó MESSAGE frame từ server chỉ ra subscription nào đã match. SEND frame của client mang destination nhưng bản thân nó không phải subscription operation.

Trong Spring WebSocket/STOMP stack, destination prefix chia ownership. Application prefix như /app route message hợp lệ về annotated application handler; broker prefix như /topic hoặc /queue route message tới configured broker. Không được suy ra queue durability, competing-consumer semantics hay topic persistence chỉ từ string; đó là capability của broker.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="stomp-headers-content">4. Header và message content</a>

<details>
<summary>Xem chi tiết</summary>

STOMP header mô tả protocol concern như destination, subscription, message id, acknowledgement mode, content type, content length, receipt request và heartbeat negotiation. Frame body là opaque đối với STOMP ngoài framing rule và metadata như content type/length.

Spring giữ ranh giới giữa protocol-native header và framework processing header. StompHeaderAccessor lưu STOMP header thật trong native-header map, còn parent SimpMessageHeaderAccessor expose common processing metadata như destination, session id, subscription id, user và message type.

Ranh giới này quan trọng khi tạo message bằng code. Một Spring header chỉ dùng nội bộ application không tự động trở thành native STOMP header gửi sang peer. Ngược lại, native header nên đi qua accessor hoặc messaging-template API phù hợp thay vì trộn tùy ý vào top-level header map.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="stomp-heartbeats-receipts">5. Heartbeat và receipt</a>

<details>
<summary>Xem chi tiết</summary>

Heartbeat phát hiện connection đang idle hoặc đã chết. Trong STOMP connection negotiation, mỗi bên khai báo tần suất có thể gửi và tần suất kỳ vọng nhận heartbeat. Giá trị sau negotiation định nghĩa liveness expectation; traffic bình thường cũng được tính là activity nên chỉ cần heartbeat khi không có frame data khác.

Receipt giải quyết vấn đề khác. Với server hỗ trợ STOMP receipt, client frame mang receipt header sẽ yêu cầu một RECEIPT; sau khi xử lý frame thành công, server gửi RECEIPT có receipt-id khớp với giá trị đã yêu cầu. Nếu processing thất bại, server có thể báo ERROR thay thế. Vì vậy receipt xác nhận protocol-level processing, không chứng minh business transaction đã commit, downstream consumer đã xử lý xong hay durable storage đã thành công.

Broker capability quyết định phần còn lại. Built-in simple broker của Spring hỗ trợ heartbeat khi được cấu hình TaskScheduler, nhưng cố ý chỉ hỗ trợ một subset của STOMP và không cung cấp full receipt semantics. Ứng dụng phụ thuộc richer protocol guarantee nên dùng external STOMP broker phù hợp qua broker relay.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="stomp-ack-transactions">6. Acknowledgement, transaction và ranh giới broker support</a>

<details>
<summary>Xem chi tiết</summary>

STOMP acknowledgement mode cho broker quyết định khi nào delivered message có thể được xem là consumed. ACK/NACK chỉ có ý nghĩa khi server hoặc broker hỗ trợ subscription acknowledgement semantics tương ứng. STOMP transaction nhóm protocol operation giữa BEGIN và COMMIT hoặc ABORT frame.

Đây là ranh giới broker support rất rõ trong Spring. Built-in simple broker được thiết kế cho in-memory subscription tracking và broadcasting, chỉ hỗ trợ một subset STOMP command. Spring reference documentation ghi rõ nó không hỗ trợ acknowledgement hoặc receipt; không nên xem nó như full transactional message broker.

Khi ứng dụng cần broker-native acknowledgement mode, durable queue, transaction, redelivery policy hoặc hành vi clustered broker, hãy dùng external broker hỗ trợ các feature đó và theo documentation của broker. Broker relay của Spring forward STOMP traffic; nó không tái định nghĩa vendor capability.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="stomp-over-websocket">7. STOMP trên WebSocket transport</a>

<details>
<summary>Xem chi tiết</summary>

Khi STOMP chạy trên WebSocket, mỗi STOMP frame được chở bên trong WebSocket message. WebSocket sở hữu connection establishment, framing và transport closure. STOMP sở hữu command, destination, subscription, heartbeat negotiation, receipt và broker-oriented message semantics.

Spring nối hai tầng bằng SubProtocolWebSocketHandler cùng STOMP codec infrastructure. Incoming WebSocket content được decode thành STOMP frame rồi biểu diễn thành Spring Message. Outbound Spring Message được encode ngược thành STOMP frame và ghi qua WebSocket session.

Mô hình nhiều tầng này rất hữu ích khi debug. Lỗi HTTP upgrade xảy ra trước khi STOMP tồn tại. WebSocket transport error có thể phá một STOMP session đã established. Malformed STOMP frame có thể fail dù WebSocket transport vẫn khỏe. Sai application destination lại có thể là Spring routing problem dù WebSocket và STOMP framing đều hợp lệ.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="simp-message-model">8. SIMP message type cùng destination, session và user headers</a>

<details>
<summary>Xem chi tiết</summary>

Simple Messaging Protocol model của Spring normalize các protocol event phổ biến thành SimpMessageType. Trong Spring Framework 6.1, các type gồm CONNECT, CONNECT_ACK, MESSAGE, SUBSCRIBE, UNSUBSCRIBE, HEARTBEAT, DISCONNECT, DISCONNECT_ACK và OTHER.

SimpMessageHeaderAccessor expose common metadata mà routing infrastructure cần: destination, session id, subscription id, user, session attributes, heartbeat information và message type. Abstraction này cho phép Spring suy luận simple-messaging event mà không buộc mọi component phụ thuộc raw STOMP header name.

SIMP là common/internal messaging model của Spring, không phải wire protocol mà client nói chuyện trực tiếp. STOMP là một protocol được adapt vào model đó. Vì vậy mã ứng dụng có thể đọc destination/user qua Spring API trong khi StompHeaderAccessor vẫn giữ native STOMP header cần để encode trả client hoặc broker.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="stomp-spring-message">9. Ánh xạ STOMP frame thành Spring Message</a>

<details>
<summary>Xem chi tiết</summary>

Cầu nối quan trọng trong STOMP support của Spring là chuyển wire frame thành Message<byte[]> cùng structured headers. StompDecoder parse command/native header, tạo StompHeaderAccessor và sinh Spring Message. Common STOMP information được project vào SIMP header để downstream component route theo destination, session, subscription, user và message type.

Chiều ngược lại, Spring infrastructure tạo hoặc nhận Message, dùng STOMP header accessor để xác định protocol command/native header phù hợp rồi StompEncoder ghi frame bytes.

Payload conversion sang domain object là bước riêng do message converter và annotated-method infrastructure xử lý sau đó. Giữ hai conversion này tách biệt tránh một nhầm lẫn phổ biến: STOMP decoding biến protocol bytes thành Spring Message; application conversion biến message payload thành Java type mà handler yêu cầu.

</details>

- [Quay lại đầu trang](#back-to-top)
