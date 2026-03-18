package com.example.learning.module.synchronization.basic.entity;

public class LivelockEntity implements Runnable {
    private String name;
    private LivelockEntity otherServer;
    private boolean sending = false;
    private static final long MAX_LIVELOCK_TIME = 5000; // 5 giây

    public LivelockEntity(String name) { this.name = name; }
    public void setOtherServer(LivelockEntity other) { this.otherServer = other; }

    @Override
    public void run() {
        long startTime = System.currentTimeMillis();

        while (true) {
            // 1. CƠ CHẾ NGẮT: Nếu loay hoay quá 5s mà không gửi được -> Hủy
            if (System.currentTimeMillis() - startTime > MAX_LIVELOCK_TIME) {
                System.err.println(name + ": [LIVELOCK DETECTED] Quá 5s va chạm liên tục. Ngắt kết nối!");
                return;
            }

            this.sending = true;
            System.out.println(name + ": Đang cố gắng gửi dữ liệu...");

            // Giả lập va chạm: Nếu cả hai cùng đang gửi
            if (otherServer.sending) {
                System.out.println(name + ": Va chạm với " + otherServer.name + "! Đang tạm dừng...");
                this.sending = false; // Lùi lại để nhường

                try {
                    // SAI LẦM GÂY LIVELOCK: Cả hai cùng đợi một khoảng thời gian cố định
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    return;
                }
                continue; // Thử lại
            }

            // Nếu không va chạm thì gửi thành công
            System.out.println(name + ": GỬI THÀNH CÔNG!");
            this.sending = false;
            break;
        }
    }
}
