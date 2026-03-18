package com.example.learning.module.coordination.pattern.service;

import org.springframework.stereotype.Service;

import java.util.concurrent.Exchanger;

@Service
public class ExchangerService {
    public void exchangerExample() {
        Exchanger<String> exchanger = new Exchanger<>();

        // Luồng Khách hàng
        new Thread(() -> {
            try {
                String moneyInHand = "100 USD";
                System.out.println("Khách: Tôi có " + moneyInHand + ", đang đợi đổi...");

                // Đổi 100 USD lấy tiền từ Quầy
                String moneyReceived = exchanger.exchange(moneyInHand);

                System.out.println("Khách: Đã nhận được " + moneyReceived);
            } catch (InterruptedException e) { e.printStackTrace(); }
        }, "Customer").start();

        // Luồng Quầy giao dịch
        new Thread(() -> {
            try {
                Thread.sleep(2000); // Giả lập nhân viên đang đếm tiền VND
                String moneyInHand = "2.500.000 VND";
                System.out.println("Quầy: Chúng tôi đã chuẩn bị xong " + moneyInHand);

                // Đổi VND lấy USD từ Khách
                String moneyReceived = exchanger.exchange(moneyInHand);

                System.out.println("Quầy: Đã nhận được " + moneyReceived + " từ khách.");
            } catch (InterruptedException e) { e.printStackTrace(); }
        }, "Bank").start();
    }
}
