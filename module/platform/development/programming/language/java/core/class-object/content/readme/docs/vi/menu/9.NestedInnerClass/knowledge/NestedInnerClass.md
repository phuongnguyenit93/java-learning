# Lớp lồng nhau và lớp nội bộ

Lớp lồng nhau là lớp được khai báo bên trong một lớp khác. Trong đó, **lớp nội bộ (inner class)** là lớp lồng nhau không `static` và gắn với một đối tượng của lớp bên ngoài. Cách tổ chức này hữu ích khi hai kiểu liên quan chặt hoặc lớp bên trong cần dùng ngữ cảnh bao quanh, nhưng từng dạng lớp lồng nhau có ngữ nghĩa khác nhau.

## <a id="static-nested-class">Lớp lồng static</a>

Lớp lồng `static` được khai báo với `static` và **không tự giữ tham chiếu tới đối tượng của lớp bên ngoài**.

```java
class BankAccount {
    static class Builder {
        BankAccount build() {
            return new BankAccount("A-01");
        }
    }
}
```

Nó gần giống một lớp bình thường về ngữ nghĩa đối tượng, chỉ được đặt trong phạm vi tên của lớp bên ngoài. Cách này phù hợp cho kiểu phụ có quan hệ logic mạnh với `BankAccount` nhưng không cần trạng thái của một tài khoản cụ thể.

Ta có thể tạo nó mà không cần một đối tượng `BankAccount`:

```java
BankAccount.Builder builder = new BankAccount.Builder();
```

## <a id="inner-class">Lớp nội bộ</a>

Lớp lồng không `static` là lớp nội bộ và gắn với một đối tượng cụ thể của lớp bên ngoài:

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

Mỗi `BalanceView` thuộc về một `BankAccount` cụ thể và có thể truy cập thành viên của đối tượng bên ngoài.

Cú pháp tạo đối tượng làm mối quan hệ này nhìn thấy rất rõ:

```java
BankAccount account = new BankAccount("A-01");
BankAccount.BalanceView view = account.new BalanceView();
```

Điều này tiện nhưng cũng tạo quan hệ phụ thuộc về thời gian tồn tại: giữ đối tượng lớp nội bộ có thể đồng thời khiến đối tượng bên ngoài tiếp tục còn được truy cập tới.

## <a id="local-anonymous-class">Lớp cục bộ và lớp vô danh</a>

Lớp cục bộ được khai báo trong khối/phương thức. Lớp vô danh tạo một cách triển khai hoặc đối tượng lớp ngay tại biểu thức mà không đặt tên kiểu riêng.

Chúng hữu ích cho hành vi cục bộ. Nếu chỉ cần triển khai một **giao diện hàm (functional interface)**, lambda thường ngắn gọn hơn; chi tiết đó thuộc mô-đun lập trình hàm/lambda.

Lớp vô danh vẫn tạo đối tượng với `this` riêng; lambda có ngữ nghĩa `this` khác.

## <a id="capture-semantics">Sử dụng biến cục bộ từ phạm vi bên ngoài</a>

Lớp cục bộ hoặc lớp vô danh chỉ có thể sử dụng một biến cục bộ từ phạm vi bên ngoài khi biến đó là `final` hoặc **effectively final** — nghĩa là sau khi được gán giá trị, nó không bị gán lại.

```java
int limit = 10;
Runnable r = new Runnable() {
    public void run() {
        System.out.println(limit);
    }
};
```

Biến cục bộ được các lớp này **capture theo giá trị**. Nếu giá trị đó là một tham chiếu, giá trị tham chiếu được capture vẫn trỏ tới cùng đối tượng; Java không tạo một bản sao sâu của đối tượng và cũng không cho lớp cục bộ/lớp vô danh chia sẻ một “ô biến cục bộ” có thể bị gán lại sau đó.

Chương tiếp theo nhìn vào lớp cha gốc chung của các lớp Java thông thường: `java.lang.Object`.
