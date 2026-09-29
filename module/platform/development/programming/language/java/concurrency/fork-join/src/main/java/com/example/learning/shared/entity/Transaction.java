package com.example.learning.shared.entity;

import lombok.Getter;
import lombok.Setter;

import java.util.concurrent.ThreadLocalRandom;

@Getter
@Setter
public class Transaction {
    private long id;
    private double amount;

    public Transaction(long id) {
        this.id = id;
        // Tạo amount ngẫu nhiên từ -500.0 đến 1000.0
        // Giả lập dữ liệu lỗi (âm) chiếm khoảng 1/3
        this.amount = ThreadLocalRandom.current().nextDouble(-500, 1000);
    }

    public boolean isValid() {
        // Giả lập một phép toán tốn CPU (như hashing hoặc regex check)
        // để thấy rõ sức mạnh của đa nhân
        double dummy = Math.sin(amount) * Math.cos(amount);
        return amount > 0;
    }
}
