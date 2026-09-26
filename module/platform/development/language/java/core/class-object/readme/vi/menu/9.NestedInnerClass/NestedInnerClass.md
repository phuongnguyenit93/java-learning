# Nested và Inner Class

Đặt một type bên trong type khác có thể thể hiện rằng chúng liên quan chặt về mặt tổ chức hoặc cần dùng ngữ cảnh bao quanh. Nhưng các dạng nested class khác nhau có ngữ nghĩa rất khác nhau.

## <a id="static-nested-class">Static Nested Class</a>

Static nested class được khai báo với `static` và **không tự giữ reference tới instance của class bên ngoài**.

```java
class BankAccount {
    static class Builder {
        BankAccount build() {
            return new BankAccount("A-01");
        }
    }
}
```

Nó gần giống một class bình thường về ngữ nghĩa instance, chỉ được đặt trong phạm vi tên của class bên ngoài. Cách này phù hợp cho type phụ có quan hệ logic mạnh với `BankAccount` nhưng không cần trạng thái của một account cụ thể.

Ta có thể tạo nó mà không cần một `BankAccount` instance:

```java
BankAccount.Builder builder = new BankAccount.Builder();
```

## <a id="inner-class">Inner Class</a>

Non-static nested class là inner class và gắn với một instance cụ thể của class bên ngoài:

```java
class BankAccount {
    private int balance;

    class BalanceView {
        int currentBalance() {
            return BankAccount.this.balance;
        }
    }
}
```

Mỗi `BalanceView` thuộc về một `BankAccount` cụ thể và có thể truy cập member của instance bên ngoài.

Cú pháp tạo object làm mối quan hệ này nhìn thấy rất rõ:

```java
BankAccount account = new BankAccount("A-01");
BankAccount.BalanceView view = account.new BalanceView();
```

Điều này tiện nhưng cũng tạo quan hệ phụ thuộc về vòng đời: giữ inner object có thể đồng thời khiến object bên ngoài tiếp tục còn reachable.

## <a id="local-anonymous-class">Local và Anonymous Class</a>

Local class được khai báo trong block/method. Anonymous class tạo một cách triển khai hoặc class instance ngay tại biểu thức mà không đặt tên type riêng.

Chúng hữu ích cho hành vi cục bộ, nhưng lambda thường đơn giản hơn nếu chỉ cần implement functional interface.

Anonymous class vẫn là object/class với `this` riêng; lambda có ngữ nghĩa `this` khác và thuộc module functional/lambda.

## <a id="capture-semantics">Captured Local Variable</a>

Local/anonymous/inner-related mã có thể capture local variable chỉ khi variable là `final` hoặc **effectively final**.

```java
int limit = 10;
Runnable r = new Runnable() {
    public void run() {
        System.out.println(limit);
    }
};
```

Local variable được capture theo giá trị phù hợp với mô hình ngôn ngữ; Java không cho local/anonymous class chia sẻ tùy ý một “ô biến cục bộ” có thể bị thay đổi sau khi capture.

Chương tiếp theo nhìn vào superclass chung của các class thông thường: `java.lang.Object`.
