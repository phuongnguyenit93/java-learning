package com.example.learning.module.coordination.classic.service;

import org.springframework.stereotype.Service;

@Service
public class ClassicCoordinationService {
    private final Object monitor = new Object();
    private boolean isDoctorReady = false;

    public void joinThread() throws InterruptedException {
        Thread dbThread = new Thread(() -> {
            System.out.println("DB: Đang kết nối database...");
            try { Thread.sleep(2000); } catch (InterruptedException e) {}
            System.out.println("DB: Kết nối thành công.");
        }, "DB-Thread");

        Thread cacheThread = new Thread(() -> {
            System.out.println("Cache: Đang khởi tạo Redis...");
            try { Thread.sleep(1000); } catch (InterruptedException e) {}
            System.out.println("Cache: Khởi tạo xong.");
        }, "Cache-Thread");

        dbThread.start();
        cacheThread.start();

        System.out.println("Main: Đang đợi các dịch vụ khởi tạo...");

        // Phối hợp: Main dừng lại đợi dbThread và cacheThread kết thúc
        dbThread.join();
        cacheThread.join();

        System.out.println("Main: Tất cả dịch vụ đã sẵn sàng. SERVER STARTED!");
    }

    public void waitAndNotifyThread() throws InterruptedException {

        // 3 bệnh nhân cùng đến đợi
        Thread p1 = new Thread(() -> patientWait("Bệnh nhân A"), "A");
        Thread p2 = new Thread(() -> patientWait("Bệnh nhân B"), "B");
        Thread p3 = new Thread(() -> patientWait("Bệnh nhân C"), "C");
        Thread p4 = new Thread(() -> patientWait("Bệnh nhân D"), "D");

        p1.start(); p2.start(); p3.start(); p4.start();

        Thread.sleep(2000); // Đợi mọi người ngủ hết đã

        // TRƯỜNG HỢP 1: Dùng notify()
        // Hệ thống sẽ chọn ngẫu nhiên một luồng duy nhất để đánh thức.
        doctorCall(false);
        doctorCall(false);

        // TRƯỜNG HỢP 2: Dùng notifyAll()
        // Từng người một sẽ chiếm được monitor, kiểm tra điều kiện while(isDoctorReady), và vào khám.
        // Đây là cách tiếp cận an toàn nhất để đảm bảo không ai bị bỏ quên.
        doctorCall(true);
    }

    private void patientWait(String name) {
        synchronized (monitor) {
            System.out.println(name + " đã vào phòng chờ.");
            while (!isDoctorReady) {
                try {
                    // Bệnh nhân đi ngủ trong phòng chờ
                    monitor.wait();
                    System.out.println(name + " vừa tỉnh giấc!");
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            // Sau khi thức dậy, bác sĩ đã sẵn sàng, bệnh nhân vào khám
            System.out.println("===> " + name + " đang được bác sĩ khám...");
            try {
                Thread.sleep(2000);
                System.out.println("===> " + name + " khám xong...");
                Thread.sleep(50);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private void doctorCall(boolean allAtOnce) {
        synchronized (monitor) {
            System.out.println("\nBác sĩ: 'Tôi đã chuẩn bị xong!'");
            isDoctorReady = true;

            if (allAtOnce) {
                System.out.println("Bác sĩ gọi: NOTIFY ALL (Tất cả dậy đi!)");
                monitor.notifyAll();
            } else {
                System.out.println("Bác sĩ gọi: NOTIFY (Chỉ một người dậy thôi!)");
                monitor.notify();
            }
        }
    }
}
