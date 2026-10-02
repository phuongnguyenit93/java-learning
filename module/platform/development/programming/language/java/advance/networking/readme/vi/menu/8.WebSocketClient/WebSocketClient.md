<a id="back-to-top"></a>

# WebSocket Client của JDK

## Menu
- [Mô hình WebSocket client trong java.net.http](#websocket-client-model)
- [Mở kết nối và opening handshake](#websocket-opening-handshake)
- [WebSocket.Listener và vòng đời callback](#websocket-listener-lifecycle)
- [request(n) và demand control](#websocket-demand-control)
- [Text/binary message fragmentation và cờ last](#websocket-message-fragmentation)
- [Gửi message và CompletionStage](#websocket-send-completion)
- [Close, abort và error handling](#websocket-close-error)
- [Ranh giới với WebSocket protocol và realtime architecture](#websocket-integration-boundary)

## <a id="websocket-client-model">Mô hình WebSocket client trong java.net.http</a>

<details>
<summary>Click for details</summary>

JDK WebSocket client nằm trong package java.net.http nhưng có data model khác HTTP request/response. Sau opening handshake, client giữ một **kết nối hai chiều, lâu sống hơn** để gửi và nhận message bất đồng bộ.

Mental model:

~~~text
HttpClient
   ↓ newWebSocketBuilder()
opening handshake
   ↓
WebSocket connection
   ↙              ↘
send messages   Listener callbacks
~~~

WebSocket API của JDK là **client API**. Nó không phải WebSocket server framework và cũng không cung cấp giao thức nhắn tin của ứng dụng như STOMP.

Các thao tác của builder/WebSocket theo mô hình non-blocking: buildAsync và các phương thức send trả CompletableFuture/CompletionStage thay vì chờ toàn bộ thao tác mạng kết thúc rồi mới return.

Điều cần giữ từ chương HTTP Client là HttpClient vẫn cung cấp hạ tầng cho WebSocket opening handshake, như proxy, executor và một số cấu hình cấp client. Sau handshake, vòng đời được điều khiển qua WebSocket và Listener.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="websocket-opening-handshake">Mở kết nối và opening handshake</a>

<details>
<summary>Click for details</summary>

Connection được mở qua WebSocket.Builder lấy từ HttpClient:

~~~java
HttpClient client = HttpClient.newHttpClient();

CompletableFuture<WebSocket> future =
        client.newWebSocketBuilder()
              .connectTimeout(Duration.ofSeconds(5))
              .buildAsync(
                      URI.create("wss://example.com/events"),
                      listener
              );
~~~

buildAsync hoàn thành bình thường với WebSocket khi opening handshake thành công. Nó có thể complete exceptionally nếu có I/O error, timeout hoặc server từ chối handshake.

Builder còn có thể cấu hình header và subprotocol. Subprotocol không phải một nhãn tùy ý của ứng dụng sau khi kết nối đã mở; nó là một phần của quá trình thương lượng handshake.

URI thường dùng scheme ws hoặc wss. Với wss, TLS nằm bên dưới connection; certificate/trust/key semantics thuộc Security & Cryptography, còn Networking chỉ cần hiểu handshake có thể thất bại vì transport/security setup.

Đừng block vô hạn bằng future.join() trong luồng xử lý request chỉ vì API trả future. Hãy quyết định rõ timeout, cancellation và vòng đời theo mô hình concurrency của ứng dụng.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="websocket-listener-lifecycle">WebSocket.Listener và vòng đời callback</a>

<details>
<summary>Click for details</summary>

WebSocket.Listener là contract phía nhận của JDK WebSocket client. Các callback chính phản ánh vòng đời:

~~~text
onOpen
  ↓
onText / onBinary / onPing / onPong
  ↓
onClose

nhánh lỗi
→ onError
~~~

Một listener đơn giản:

~~~java
WebSocket.Listener listener = new WebSocket.Listener() {
    @Override
    public void onOpen(WebSocket webSocket) {
        webSocket.request(1);
    }

    @Override
    public CompletionStage<?> onText(
            WebSocket webSocket,
            CharSequence data,
            boolean last) {

        System.out.println(data);
        webSocket.request(1);
        return null;
    }
};
~~~

JDK sắp xếp các callback nhận của cùng một WebSocket theo thứ tự; ứng dụng không nên giả định cùng listener callback được gọi song song tùy ý rồi tự tạo race condition không cần thiết.

Ranh giới của tính tuần tự này cần hiểu chính xác: callback kế tiếp chỉ bắt đầu sau khi **method callback trước return**. Với `onText`, `onBinary`, `onPing` và `onPong`, `CompletionStage` được trả về chủ yếu cho WebSocket biết khi nào dữ liệu callback có thể được thu hồi; việc stage hoàn tất **không** làm tăng demand counter và cũng không phải điều kiện bắt buộc để một callback khác đã được request bắt đầu.

Callback là nơi xử lý sự kiện mạng, không phải nơi phù hợp để chạy business logic blocking dài. Nếu handoff xử lý nặng sang executor khác rồi callback return ngay, callback sau có thể bắt đầu khi demand cho phép, nên trạng thái ứng dụng lúc này có thể bị xử lý đồng thời. Phải quản lý rõ cả demand lẫn trách nhiệm sở hữu việc xử lý thay vì giả định `CompletionStage` tự serialize business work.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="websocket-demand-control">request(n) và demand control</a>

<details>
<summary>Click for details</summary>

JDK WebSocket client không tự đẩy vô hạn receive callback cho listener. Ứng dụng thể hiện **demand** bằng WebSocket.request(n).

Ví dụ:

~~~java
@Override
public void onOpen(WebSocket webSocket) {
    webSocket.request(1);
}

@Override
public CompletionStage<?> onText(
        WebSocket webSocket,
        CharSequence data,
        boolean last) {

    handle(data);
    webSocket.request(1);
    return null;
}
~~~

request(1) nghĩa là cho phép thêm một lần gọi nhận, không phải “đọc đúng một thông điệp ứng dụng hoàn chỉnh”. Counter này áp dụng cho `onText`, `onBinary`, `onPing`, `onPong` **và `onClose`**. `onOpen` và `onError` không phải receive method bị demand kiểm soát; đặc biệt `onError` có thể được gọi bất kể counter hiện tại.

Text/binary message có thể bị fragment thành nhiều callback, vì vậy demand phải được hiểu cùng fragmentation. `CompletionStage` trả về từ receive callback là cơ chế riêng với counter này: chính `request(n)` mới tăng demand.

Demand control giúp listener không bị gọi nhanh hơn mức ứng dụng chủ động yêu cầu. Tuy nhiên đây không biến WebSocket thành một reactive-streams API hoàn chỉnh; ngữ nghĩa của Flow/Reactive Streams thuộc module khác.

Sai lầm phổ biến là quên request thêm sau onOpen hoặc callback, khiến kết nối vẫn tồn tại nhưng ứng dụng không nhận thêm sự kiện như mong đợi.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="websocket-message-fragmentation">Text/binary message fragmentation và cờ last</a>

<details>
<summary>Click for details</summary>

Một WebSocket text hoặc binary message có thể đến qua **nhiều callback fragment**. Tham số last cho biết fragment hiện tại có phải phần cuối của message hay không.

~~~java
StringBuilder current = new StringBuilder();

@Override
public CompletionStage<?> onText(
        WebSocket webSocket,
        CharSequence data,
        boolean last) {

    current.append(data);

    if (last) {
        processMessage(current.toString());
        current.setLength(0);
    }

    webSocket.request(1);
    return null;
}
~~~

Do đó không được giả định:

~~~text
one onText callback = one application message
~~~

Binary callback có cùng ý tưởng nhưng dùng ByteBuffer. Nếu cần ghép fragment, ứng dụng phải quản lý bộ tích lũy/trạng thái cẩn thận và đặt giới hạn để tránh peer gửi message quá lớn làm tăng bộ nhớ không kiểm soát.

Fragmentation ở đây là hành vi của API phía nhận cần biết để viết code đúng. Protocol framing sâu hơn thuộc module WebSocket protocol/realtime.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="websocket-send-completion">Gửi message và CompletionStage</a>

<details>
<summary>Click for details</summary>

Send-side API trả CompletableFuture<WebSocket>, cho biết thao tác gửi tương ứng đã hoàn thành theo contract của WebSocket API.

~~~java
webSocket.sendText("hello", true)
         .thenRun(() -> System.out.println("sent"));
~~~

Các nhóm send chính:

- sendText(...);
- sendBinary(...);
- sendPing(...);
- sendPong(...);
- sendClose(...).

Boolean last của sendText/sendBinary cho phép ứng dụng chủ động fragment một message khi cần.

Việc send future hoàn tất không đồng nghĩa peer nghiệp vụ đã xử lý message. Nó chỉ phản ánh thao tác gửi phía client. Nếu ứng dụng cần acknowledgement ở tầng nghiệp vụ, phải thiết kế protocol/message riêng.

Giữ distinction:

~~~text
send operation completed
≠ remote business action completed
~~~

Send còn có quy tắc về thao tác đang pending. Nếu bắt đầu text/binary send mới khi một text/binary send trước chưa hoàn tất, hoặc đổi giữa text/binary khi fragmented message trước chưa kết thúc, future có thể complete exceptionally với `IllegalStateException`. Ping/Pong cũng có giới hạn tương tự đối với control-message send đang pending.

Hãy compose các stage gửi hoặc dùng queue/serialization rõ ràng để mỗi lần gửi logic tuân thủ ràng buộc trạng thái:

~~~java
webSocket.sendText("part-1", false)
         .thenCompose(ws -> ws.sendText("part-2", true));
~~~

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="websocket-close-error">Close, abort và error handling</a>

<details>
<summary>Click for details</summary>

WebSocket có hai hướng kết thúc đáng phân biệt: **protocol close handshake** và **abort**.

sendClose(code, reason) khởi động việc gửi Close message theo protocol:

~~~java
webSocket.sendClose(
        WebSocket.NORMAL_CLOSURE,
        "done"
);
~~~

Listener.onClose(...) quan sát Close message từ peer và trả CompletionStage để biểu diễn việc xử lý callback.

`sendClose(...)` đóng phía **output** của WebSocket bằng cách gửi Close message; nó không đóng input ngay lập tức. Input tiếp tục mở cho tới khi nhận Close từ peer, `abort()` được gọi hoặc lỗi đóng WebSocket.

`onClose(...)` là callback cuối cùng từ WebSocket đó. Nếu output vẫn còn mở khi nhận Close từ peer, stage mà `onClose` trả về cho cách triển khai biết khi nào có thể đóng output để đáp lại; trả `null` nghĩa là có thể thực hiện ngay.

abort() là đường kết thúc mạnh hơn: đóng kết nối ngay thay vì hoàn tất graceful close handshake. Nó phù hợp cho tình huống ứng dụng không thể hoặc không muốn tiếp tục vòng đời protocol bình thường.

onError(...) báo lỗi phía nhận/vòng đời. Khi lỗi xảy ra, đừng chỉ log rồi giả định cùng WebSocket tiếp tục hoạt động bình thường. Hãy đưa trạng thái kết nối về trạng thái xác định và để chính sách reconnect ở tầng ứng dụng, nếu có, nằm ở layer sở hữu hành vi resilience/realtime.

`onError(...)` là terminal đối với WebSocket đó: đây là listener invocation cuối cùng, và cả input lẫn output đã đóng khi callback bắt đầu. Khác với receive method, callback này không bị `request(n)` kiểm soát.

Reason/status của Close là thông tin protocol; lỗi nghiệp vụ nên có message/schema riêng thay vì overload close code.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="websocket-integration-boundary">Ranh giới với WebSocket protocol và realtime architecture</a>

<details>
<summary>Click for details</summary>

Chương Networking sở hữu **cơ chế JDK client**:

- xây dựng/mở kết nối;
- vòng đời callback của Listener;
- demand;
- fragmentation;
- hành vi send/close/error.

Nó không sở hữu:

- thiết kế WebSocket server;
- reconnect/backoff policy;
- chiến lược heartbeat của ứng dụng;
- STOMP hoặc messaging broker semantics;
- channel/topic/subscription model;
- luồng authentication ở protocol ứng dụng;
- distributed realtime architecture.

Mental handoff:

~~~text
JDK WebSocket API
→ làm thế nào Java client gửi/nhận

Realtime / Integration
→ message có ý nghĩa gì
→ reconnect/subscribe thế nào
→ topology và delivery semantics ra sao
~~~

Ranh giới này đặc biệt quan trọng khi dùng framework như Spring WebSocket: framework có thể che các chi tiết transport của JDK/client, nhưng các khái niệm protocol/ứng dụng vẫn không trở thành trách nhiệm của core Java Networking.

Chương cuối của module sẽ gom các lựa chọn TCP, UDP, NIO, HttpClient và WebSocket vào một mô hình quyết định chung để người học biết khi nào nên dùng abstraction nào và khi nào phải handoff sang Security, Concurrency hoặc Integration.

</details>

- [Quay lại đầu trang](#back-to-top)
