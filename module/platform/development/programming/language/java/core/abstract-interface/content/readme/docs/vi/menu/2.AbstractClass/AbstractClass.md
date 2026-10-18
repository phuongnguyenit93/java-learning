# Lớp trừu tượng (abstract class)

Chương trước đã xác định hai nhu cầu khác nhau: **hợp đồng mà bên sử dụng nhìn thấy** và **phần trạng thái/hành vi mà một họ lớp muốn dùng chung**. Lớp trừu tượng phù hợp nhất với nhu cầu thứ hai khi các lớp con có quan hệ gần nhau.

## <a id="abstract-class-model">Lớp trừu tượng là gì?</a>

### KHÁI NIỆM

Một `abstract class` là lớp **không thể tạo đối tượng trực tiếp**, nhưng vẫn có thể chứa:

- trạng thái của đối tượng qua trường (field);
- hàm khởi tạo (constructor);
- phương thức có phần thân;
- phương thức `abstract` không có phần thân.

Một lớp được khai báo `abstract` cũng **không bắt buộc** phải có phương thức `abstract`. Từ khóa `abstract` trên lớp trước hết nói rằng lớp đó chưa phải một kiểu có thể được khởi tạo trực tiếp.

Vì vậy lớp trừu tượng thường đóng vai trò **khung triển khai một phần** cho một nhóm lớp có quan hệ gần nhau.

```java
abstract class BasePayment {
    private final String provider;

    protected BasePayment(String provider) {
        this.provider = provider;
    }

    String provider() {
        return provider;
    }

    public abstract void pay(int amount);
}
```

`BasePayment` giữ trạng thái chung là `provider`, cung cấp hành vi `provider()` và buộc lớp con hoàn thiện `pay(...)`.

### VÌ SAO

Nếu nhiều lớp có chung trạng thái, quy tắc khởi tạo, bất biến (invariant) và một số hành vi nền tảng, sao chép các phần đó sang từng lớp sẽ tạo trùng lặp và làm quy tắc chung khó duy trì. Lớp trừu tượng giữ phần dùng chung ở một nơi nhưng vẫn để lại những điểm mở mà lớp con phải hoàn thiện.

Trong chương sau, `PaymentMethod` sẽ được đặt phía trên để tách **hợp đồng mà bên sử dụng phụ thuộc** khỏi **phần triển khai chung** mà một nhánh lớp muốn tái sử dụng.

## <a id="abstract-method">Phương thức abstract</a>

### KHÁI NIỆM

Phương thức `abstract` chỉ khai báo chữ ký phương thức mà không có phần thân:

```java
abstract void pay(int amount);
```

Một lớp con cụ thể phải bảo đảm mọi yêu cầu `abstract` đã được đáp ứng bằng phần triển khai hợp lệ trước khi có thể tạo đối tượng. Lớp con không nhất thiết phải tự khai báo lại phương thức nếu nó đã kế thừa một phần triển khai cụ thể tương thích từ một lớp trung gian trong hệ phân cấp.

### CƠ CHẾ

Phương thức `abstract` vẫn tuân theo các quy tắc ghi đè (override) thông thường của Java:

- mức truy cập phải tương thích;
- kiểu trả về phải tương thích, bao gồm kiểu trả về đồng biến (covariant return) khi áp dụng;
- ngoại lệ được kiểm tra (checked exception) phải tuân quy tắc override;
- `@Override` giúp trình biên dịch kiểm tra đúng quan hệ ghi đè.

`abstract` không tạo ra một cơ chế gọi phương thức riêng. Khi lớp con cung cấp phần triển khai, lời gọi phương thức của đối tượng vẫn sử dụng **phân phối động (dynamic dispatch)** như các phương thức ghi đè khác.

## <a id="abstract-constructor">Hàm khởi tạo của lớp trừu tượng</a>

Một lớp trừu tượng không thể được `new` trực tiếp, nhưng hàm khởi tạo của nó **vẫn chạy** khi tạo đối tượng của lớp con cụ thể:

```java
class CardPayment extends BasePayment {
    CardPayment() {
        super("card");
    }

    @Override
    public void pay(int amount) { ... }
}
```

Hàm khởi tạo của `BasePayment` không được lớp con “kế thừa”. Nó được gọi trong chuỗi khởi tạo để thiết lập phần trạng thái thuộc `BasePayment` của đối tượng `CardPayment` trước khi quá trình khởi tạo lớp con hoàn tất.

### GIỚI HẠN

Không nên gọi phương thức có thể bị ghi đè từ hàm khởi tạo nếu không có lý do rất mạnh. Khi đó phần triển khai của lớp con có thể chạy trước khi trạng thái riêng của lớp con được khởi tạo đầy đủ.

## <a id="abstract-class-limits">Giới hạn của lớp trừu tượng</a>

`abstract` chỉ ngăn việc tạo đối tượng trực tiếp. Lớp trừu tượng vẫn dùng được làm kiểu tham chiếu:

```java
BasePayment payment = new CardPayment();
```

Giới hạn quan trọng hơn là Java chỉ cho một lớp `extends` **một lớp**. Vì vậy nếu một khả năng cần xuất hiện ở nhiều cây kế thừa không liên quan, ép tất cả vào cùng một lớp cha trừu tượng sẽ tạo ràng buộc không cần thiết.

Đó là lý do bước tiếp theo chuyển sang `interface`: **nếu ta chỉ cần một hợp đồng chung mà không muốn mọi cách triển khai phải thuộc cùng một cây kế thừa lớp thì sao?**
