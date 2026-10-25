# Cơ chế dự phòng của ResourceBundle

Bản địa hóa hiếm khi có một tệp riêng hoàn hảo cho mọi tổ hợp ngôn ngữ-hệ chữ-khu vực. `ResourceBundle` vì vậy có **cơ chế dự phòng (fallback)**: khi tài nguyên cụ thể không tồn tại, Java có thể tiếp tục tra cứu bằng các Locale ứng viên tổng quát hơn hoặc bằng nhánh Locale dự phòng do chính sách tra cứu quy định.

Cơ chế dự phòng hữu ích, nhưng cần hiểu rõ để không nhầm “ứng dụng vẫn chạy” với “bản dịch đã đầy đủ”.

Nói đơn giản, **cơ chế dự phòng (fallback) là chiến lược tiếp tục tra cứu khi tài nguyên phù hợp nhất với Locale yêu cầu không tồn tại**.

Nó tồn tại vì nếu không có cơ chế dự phòng, hệ thống sẽ phải có một tệp hoàn chỉnh cho mọi Locale cụ thể hoặc thất bại ngay khi thiếu một biến thể nhỏ. Cơ chế này cho phép tái sử dụng tài nguyên tổng quát hơn, nhưng đổi lại lập trình viên phải hiểu rõ tài nguyên cuối cùng đến từ đâu.

Các mảnh tham gia vào quá trình này gồm:

```text
Locale yêu cầu
→ Locale người dùng/ngữ cảnh đang yêu cầu

Locale ứng viên
→ các Locale do `ResourceBundle.Control` sinh ra cho một lần tra cứu

Locale mặc định dùng cho dự phòng
→ nhánh tra cứu bổ sung theo chính sách mặc định của ResourceBundle

gói tài nguyên gốc (`base bundle`)
→ tài nguyên không có hậu tố Locale

hành vi khi thiếu tài nguyên
→ chuyện gì xảy ra nếu cuối cùng vẫn không có gói tài nguyên/khóa phù hợp
```

## <a id="bundle-candidate-chain">Chuỗi Locale ứng viên của ResourceBundle</a>

Giả sử tên cơ sở (`base name`) là `Messages` và Locale là `en-US`.

Các gói tài nguyên có thể có:

```text
Messages_en_US.properties
Messages_en.properties
Messages.properties
```

Mô hình tư duy ở mức nhập môn:

```text
Locale yêu cầu càng cụ thể
        ↓
ứng viên cụ thể hơn được ưu tiên
        ↓
nếu không có thì thử ứng viên tổng quát hơn
        ↓
`Locale.ROOT` đại diện cho gói tài nguyên gốc trong danh sách ứng viên
```

`ResourceBundle.Control` cho phép quan sát danh sách Locale ứng viên:

```java
ResourceBundle.Control control = ResourceBundle.Control.getControl(
        ResourceBundle.Control.FORMAT_DEFAULT
);

List<Locale> candidates = control.getCandidateLocales(
        "Messages",
        Locale.forLanguageTag("en-US")
);
```

Với Locale có hệ chữ/biến thể, danh sách có thể phức tạp hơn ví dụ `en-US` đơn giản. Hãy dựa vào API thay vì tự viết thuật toán tra cứu bằng cách nối tên tệp.

Điểm cần tách rõ là **việc tạo danh sách Locale ứng viên** và **việc chọn Locale dự phòng** không phải cùng một bước. `getCandidateLocales(...)` tạo danh sách ứng viên cho *một* Locale đang xét. Nếu chính sách tra cứu cho phép thử một Locale dự phòng khác (mặc định có thể là Locale mặc định của JVM), Java có thể tạo **một danh sách ứng viên mới** cho Locale dự phòng đó. Vì vậy không nên ghi nhớ một chuỗi tên tệp cố định rồi tự mô phỏng hành vi của `ResourceBundle`.

## <a id="default-locale-fallback">Dự phòng qua Locale mặc định</a>

Ngoài danh sách ứng viên của Locale được yêu cầu, cơ chế `ResourceBundle` mặc định còn có thể tham chiếu **Locale mặc định của JVM** như một nhánh dự phòng khi không tìm được gói tài nguyên phù hợp cho Locale đích.

Đây là lý do Locale mặc định có thể trở thành **phụ thuộc ẩn** nguy hiểm.

Ví dụ:

```text
yêu cầu muốn fr-FR
        ↓
không có gói tài nguyên tiếng Pháp phù hợp
        ↓
Locale mặc định của JVM là en-US
        ↓
có thể đi qua nhánh ứng viên tiếng Anh theo chính sách dự phòng
```

Ứng dụng có thể “không lỗi” nhưng người dùng lại nhận nội dung bằng ngôn ngữ không mong đợi.

Trong hệ thống cần chính sách chặt chẽ, có thể tùy biến `ResourceBundle.Control` để kiểm soát cơ chế dự phòng:

```java
ResourceBundle.Control control = ResourceBundle.Control.getNoFallbackControl(
        ResourceBundle.Control.FORMAT_DEFAULT
);

ResourceBundle bundle = ResourceBundle.getBundle(
        "Messages",
        locale,
        control
);
```

Đừng tùy biến chỉ vì API cho phép; hãy bắt đầu từ quy tắc sản phẩm: Locale không được hỗ trợ thì dùng ngôn ngữ dự phòng nào, gói tài nguyên gốc nào, hay trả lỗi?

## <a id="base-bundle">Vai trò của gói tài nguyên gốc</a>

Gói tài nguyên gốc không có hậu tố Locale:

```text
Messages.properties
```

Nó có thể đóng vai trò tập tài nguyên tổng quát cuối cùng. Tuy nhiên với chính sách mặc định, đừng suy luận rằng Java luôn “trả gói gốc ngay” trước khi xem xét nhánh Locale dự phòng; việc chọn gói tài nguyên cuối cùng còn phụ thuộc toàn bộ thuật toán của `ResourceBundle.Control`.

Có hai chiến lược phổ biến:

```text
Chiến lược A
gói tài nguyên gốc = ngôn ngữ mặc định thực tế, ví dụ tiếng Anh

Chiến lược B
gói tài nguyên gốc = tập giá trị dự phòng tối thiểu/an toàn cho kỹ thuật
các gói theo Locale chứa nội dung ngôn ngữ đầy đủ
```

Điều quan trọng là chiến lược phải **nhất quán và được kiểm thử**. Nếu gói gốc chứa tiếng Anh nhưng nhóm phát triển tưởng nó “trung lập theo Locale”, bản dịch bị thiếu có thể âm thầm hiện tiếng Anh ở giao diện của ngôn ngữ khác.

Khóa được tìm qua chuỗi cha của gói tài nguyên đã được phân giải. Vì vậy một gói theo Locale không nhất thiết phải sao chép toàn bộ khóa nếu gói cha/gốc đã cung cấp giá trị — nhưng sản phẩm vẫn có thể yêu cầu đầy đủ 100% và kiểm tra bằng kiểm thử/kiểm tra trong quá trình xây dựng.

## <a id="missing-resource">MissingResourceException và tài nguyên bị thiếu</a>

Hai trường hợp lỗi cần phân biệt:

```text
không tìm được gói tài nguyên hợp lệ
→ ResourceBundle.getBundle(...) có thể ném MissingResourceException

gói tài nguyên tồn tại nhưng khóa không tồn tại trong chuỗi tra cứu
→ getString(key) có thể ném MissingResourceException
```

Ví dụ:

```java
try {
    String message = bundle.getString("order.created");
} catch (MissingResourceException ex) {
    // ghi log/chuyển đổi/dự phòng theo chính sách của ứng dụng
}
```

Trong môi trường thật, tốt hơn là phát hiện lỗi sớm bằng kiểm thử:

```text
khóa của gói gốc
        ↓ so sánh
khóa của từng Locale
        ↓
báo khóa bị thiếu / dư
```

Không nên bắt mọi `MissingResourceException` rồi trả khóa như văn bản mà không ghi log/giám sát, vì điều đó có thể biến lỗi triển khai thành giao diện sai tồn tại lâu.

Cơ chế dự phòng là lớp an toàn và cơ chế tái sử dụng, không phải lý do để bỏ qua chất lượng bản dịch.

Chương cuối gom các lỗi thực tế khi lập trình viên để Locale mặc định hoặc văn bản đã định dạng len vào những nơi cần dữ liệu ổn định.
