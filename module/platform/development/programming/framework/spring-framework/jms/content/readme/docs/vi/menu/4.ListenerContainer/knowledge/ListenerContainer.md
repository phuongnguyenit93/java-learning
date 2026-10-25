<a id="back-to-top"></a>

# Mô hình Message Listener Container

## Menu
- [Mô hình Message-Driven POJO](#jms-mdp-model)
- [Listener Container chịu trách nhiệm những gì?](#jms-listener-container-responsibilities)
- [Mô hình tư duy của DefaultMessageListenerContainer](#jms-dmlc-model)
- [DefaultMessageListenerContainer và SimpleMessageListenerContainer](#jms-listener-container-choices)
- [MessageListenerAdapter và POJO Delegation](#jms-message-listener-adapter)
- [Ranh giới JCA Message Endpoint](#jms-jca-endpoint-boundary)

## <a id="jms-mdp-model">Mô hình Message-Driven POJO</a>

<details>
<summary>Xem chi tiết</summary>

Một ứng dụng message-driven không nên bắt đối tượng nghiệp vụ tự sở hữu vòng `MessageConsumer.receive()` vô tận, logic kết nối lại, tạo lại consumer và phối hợp shutdown. Mô hình **Message-Driven POJO** của Spring tách các trách nhiệm lúc chạy đó khỏi đối tượng xử lý nghiệp vụ.

Mô hình tư duy:

```text
JMS provider
    ↓
Spring MessageListenerContainer
    ↓
adapter / endpoint invocation
    ↓
application POJO
```

Nhờ vậy POJO có thể tập trung vào phương thức kiểu `handle(OrderCommand command)`, còn framework quyết định khi nào và bằng cách nào phương thức được gọi. Tùy cách cấu hình, cơ chế chuyển từ message sang lời gọi phương thức có thể được khai báo trực tiếp qua `MessageListenerAdapter` hoặc do hạ tầng annotation-driven endpoint của chương sau cung cấp.

Lợi ích chính là container sở hữu lifecycle. Một consumer chạy hàng giờ hoặc hàng ngày phải xử lý thứ tự startup, provider tạm thời gián đoạn, stop/restart, transaction boundary và thay đổi concurrency. Đây là trách nhiệm của container, không phải thứ cần được viết lại trong mỗi message handler.

“POJO” không có nghĩa lớp JMS biến mất. Conversion vẫn có thể thất bại, listener vẫn có thể chạy trong JMS transaction, và redelivery vẫn xảy ra theo acknowledgement/transaction semantics đã cấu hình. Mô hình này chỉ giúp mã ứng dụng phụ thuộc vào hợp đồng phương thức sạch hơn trong khi ranh giới tích hợp vẫn được thể hiện rõ qua cấu hình.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="jms-listener-container-responsibilities">Listener Container chịu trách nhiệm những gì?</a>

<details>
<summary>Xem chi tiết</summary>

`MessageListenerContainer` là thành phần chạy lâu dài. Nó sở hữu hạ tầng cần thiết để biến message từ provider thành lời gọi vào ứng dụng có kiểm soát.

Trong họ JMS listener container của Spring, các trách nhiệm cốt lõi gồm lifecycle của connection/session/consumer, hành vi start/stop, destination và listener configuration, dispatch vào listener đã cấu hình, cùng tích hợp với acknowledgement/transaction settings. Từng cách triển khai bổ sung strategy riêng cho concurrency, polling, recovery và caching.

Việc container sở hữu lifecycle này thay đổi cách viết application listener. Handler nên giả định container đang sở hữu receive lifecycle bên ngoài. Nó không được tự close `Session`, stop consumer hoặc giữ provider resource lại để dùng sau.

Container cũng là nơi đặt chính sách vận hành. Concurrency, thời gian recovery, task execution, transaction manager, selector, tùy chọn durable subscription và caching có thể thay đổi mà không phải viết lại business handler.

Sự tách này cũng giúp hiểu annotation-driven JMS đúng hơn: `@JmsListener` không phải “một annotation tự tạo background thread”. Nó mô tả endpoint mà Spring sẽ hiện thực hóa bằng listener container có lifecycle và quy tắc tài nguyên cụ thể.

Khi debug handler không được gọi, nên kiểm tra cả hai phía của boundary: container có đang running không, đã kết nối chưa, có nghe đúng destination không, và có convert/dispatch được message không trước khi kết luận lỗi nằm trong business method.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="jms-dmlc-model">Mô hình tư duy của DefaultMessageListenerContainer</a>

<details>
<summary>Xem chi tiết</summary>

`DefaultMessageListenerContainer` (DMLC) là listener container standalone linh hoạt của Spring cho plain JMS environment. Cơ chế trung tâm của nó là một vòng lặp `MessageConsumer.receive()` chạy trong các asynchronous invoker task, thay vì đăng ký một provider callback rồi giao toàn bộ receive lifecycle cho `setMessageListener`.

Khi startup, DMLC tạo số consumer invoker theo `concurrentConsumers`. Mỗi invoker làm việc với JMS resource và lặp việc receive/dispatch message. Nếu `maxConcurrentConsumers` lớn hơn, DMLC có thể tạo thêm invoker khi tải tăng rồi giảm về baseline khi tải hạ. Spring `TaskExecutor` được cấu hình sẽ quyết định các work unit bất đồng bộ này chạy như thế nào.

DMLC cũng được thiết kế để phục hồi khi provider tạm thời không khả dụng. Nó có thể lấy lại JMS handle sau lỗi và hỗ trợ stop/restart như một `SmartLifecycle` component. Đây là lý do DMLC thường là mô hình tư duy chính cho Spring JMS consumer trong production.

Vòng receive của DMLC cũng phù hợp với việc nhận message có transaction. Có thể dùng `PlatformTransactionManager` bên ngoài bao quanh quá trình receive/gọi listener, và container có chính sách cache tài nguyên riêng. Ngữ nghĩa transaction và cache level chi tiết thuộc chương sau; ở đây cần nhớ DMLC sở hữu cả **vòng đời consumer** lẫn **quá trình receive**.

Không nên suy ra “thêm concurrent consumer luôn tăng throughput”. Yêu cầu về ordering, hành vi topic subscription, provider capacity, downstream resource và transaction cost đều giới hạn concurrency hữu ích. DMLC cung cấp cơ chế điều khiển; nó không xóa các đánh đổi của hệ thống.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="jms-listener-container-choices">DefaultMessageListenerContainer và SimpleMessageListenerContainer</a>

<details>
<summary>Xem chi tiết</summary>

Spring còn có `SimpleMessageListenerContainer` (SMLC). Khác biệt với DMLC là khác biệt kiến trúc chứ không chỉ là ít option hơn.

SMLC dùng cơ chế `MessageConsumer.setMessageListener(...)` của JMS client và tạo **số lượng cố định** các `Session`/consumer. Nó không dynamic scale consumer count theo runtime load. Vì vậy SMLC phù hợp khi provider-driven callback model đơn giản và fixed concurrency đã đủ.

DMLC ngược lại chủ động nhận message qua vòng `receive()`, chạy listener invoker qua Spring `TaskExecutor`, hỗ trợ dynamic scaling giữa lower/upper concurrency bound và có khả năng recovery/runtime management mạnh hơn. Thiết kế này cũng hỗ trợ tốt các transactional receive pattern cần kiểm soát quanh từng receive-and-invoke unit.

Câu hỏi chọn container nên dựa trên yêu cầu lúc chạy:

```text
fixed consumer + provider callback đơn giản
        → SimpleMessageListenerContainer

Spring-controlled receive loop + recovery/scaling/transaction linh hoạt
        → DefaultMessageListenerContainer
```

Không chọn SMLC chỉ vì tên có chữ “Simple”, cũng không chọn DMLC chỉ vì nó xuất hiện nhiều trong ví dụ. Hãy chọn theo lifecycle và transaction model thực tế. Với phần lớn standalone Spring JMS application cần vận hành bền vững, DMLC thường là baseline mạnh hơn; SMLC vẫn hợp lý khi fixed model đơn giản đã đủ.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="jms-message-listener-adapter">MessageListenerAdapter và POJO Delegation</a>

<details>
<summary>Xem chi tiết</summary>

`MessageListenerAdapter` là bridge tường minh từ JMS listener contract sang một Java object thông thường. Nó implement các listener-facing interface rồi delegate việc xử lý message vào target method bằng reflection.

Mặc định delegate method có tên `handleMessage`. Trước khi gọi method, adapter dùng Spring JMS `MessageConverter`—mặc định là `SimpleMessageConverter`—để extract content, vì vậy target có thể nhận `String`, `byte[]`, `Map` hoặc payload đã convert thay vì buộc phải nhận raw JMS `Message`.

```java
class OrderHandler {
    public void handleMessage(String payload) {
        // xử lý nghiệp vụ
    }
}
```

Adapter cũng có thể tạo reply nếu delegate method trả về value khác `null`. Giá trị trả về được convert thành JMS message rồi gửi tới reply destination của message đi vào hoặc default response destination đã cấu hình. Có một ranh giới quan trọng: việc tự động gửi response khả dụng khi adapter được dùng qua `SessionAwareMessageListener` entry point của Spring listener container; nếu chỉ dùng adapter như standard JMS `MessageListener`, đường tạo response đó không có.

`MessageListenerAdapter` làm mô hình Message-Driven POJO trở nên cụ thể, nhưng không phải cơ chế adaptation duy nhất. `@JmsListener` có endpoint/method-resolution infrastructure phong phú hơn. Hãy giữ mô hình tư duy: container nhận JMS message; adapter convert/delegate; POJO xử lý dữ liệu nghiệp vụ.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="jms-jca-endpoint-boundary">Ranh giới JCA Message Endpoint</a>

<details>
<summary>Xem chi tiết</summary>

Spring cũng có thể đưa JMS listener ra theo mô hình **JCA message endpoint**. Đây là đường tích hợp thay thế cho môi trường dùng Jakarta Connectors resource adapter, thường gặp trong deployment dùng application server/resource adapter.

`JmsMessageEndpointManager` mở rộng cơ chế quản lý JCA endpoint dùng chung của Spring và bổ sung hỗ trợ cho JMS `ActivationSpec`. `JmsActivationSpecConfig` mô tả các thiết lập kích hoạt JMS phổ biến; activation-spec factory chuyển cấu hình đó thành JCA `ActivationSpec` đặc thù provider để kích hoạt endpoint.

Mô hình sở hữu tài nguyên khác DMLC standalone:

```text
standalone Spring JMS
    → Spring listener container sở hữu receive loop/resource

JCA endpoint environment
    → ResourceAdapter + ActivationSpec điều khiển endpoint activation
```

Contract Spring 6.1 cũng có giới hạn rõ: JCA endpoint manager hỗ trợ standard JMS `MessageListener`, nhưng không hỗ trợ Spring `SessionAwareMessageListener` vì JCA endpoint contract không cho listener lấy current JMS `Session` theo cách đó.

Không nên xem JCA như “advanced mode” bắt buộc của mọi JMS application. Nó tồn tại cho một deployment/integration model cụ thể. Với standalone Spring application dùng JMS `ConnectionFactory`, DMLC và annotation-driven listener endpoint là đường đi thông thường; JCA phù hợp khi runtime architecture đã dựa trên resource adapter và endpoint activation.

</details>

- [Quay lại đầu trang](#back-to-top)
