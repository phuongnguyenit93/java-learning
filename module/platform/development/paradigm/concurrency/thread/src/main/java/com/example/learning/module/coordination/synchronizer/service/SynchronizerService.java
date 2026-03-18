package com.example.learning.module.coordination.synchronizer.service;

import org.springframework.stereotype.Service;

import java.util.concurrent.*;

@Service
public class SynchronizerService {
    public void countDownLatchSample() throws InterruptedException {
        // Chúng ta cần 2 nguồn dữ liệu hoàn tất
        CountDownLatch latch = new CountDownLatch(2);

        System.out.println("[Main] Bắt đầu chuẩn bị báo cáo...");

        // Luồng 1: Lấy dữ liệu bán hàng (Dùng Lambda trực tiếp)
        new Thread(() -> {
            try {
                System.out.println("[Sales] Đang truy vấn database bán hàng...");
                Thread.sleep(2000); // Giả lập độ trễ
                System.out.println("[Sales] Lấy dữ liệu bán hàng THÀNH CÔNG.");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                latch.countDown(); // Báo cáo xong phần việc 1
            }
        }).start();

        // Luồng 2: Lấy dữ liệu kho (Dùng Lambda trực tiếp)
        new Thread(() -> {
            try {
                System.out.println("[Inventory] Đang kiểm tra tồn kho qua API...");
                Thread.sleep(3000); // Giả lập độ trễ
                System.out.println("[Inventory] Lấy dữ liệu kho THÀNH CÔNG.");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                latch.countDown(); // Báo cáo xong phần việc 2
            }
        }).start();

        // Luồng Main đứng đợi
        System.out.println("[Main] Đang đợi dữ liệu từ các bộ phận...");
        latch.await();

        System.out.println("[Main] ĐÃ ĐỦ DỮ LIỆU. Đang xuất báo cáo PDF...");
    }

    public void countDownLatchTimeOut() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);

        // Luồng này làm việc cực lâu (5 giây)
        new Thread(() -> {
            try {
                Thread.sleep(5000);
                latch.countDown();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }).start();

        System.out.println("[Main] Tôi chỉ đợi tối đa 2 giây thôi...");

        // Đợi 2 giây
        boolean completed = latch.await(2, TimeUnit.SECONDS);

        if (completed) {
            System.out.println("[Main] Tuyệt vời! Mọi thứ đã xong.");
        } else {
            // Vượt quá thời gian, nó chạy vào đây
            System.err.println("[Main] QUÁ GIỜ RỒI! Tôi không đợi nữa, hủy khởi động hệ thống.");
            // Lưu ý: Luồng phụ ở trên vẫn đang chạy ngầm, Latch không tự hủy nó.
        }

        System.out.println("[Main] Kết thúc hàm main.");
    }

    public void cyclicBarrierDemo(int timeWait) throws InterruptedException {
        // Khởi tạo Barrier cho 3 người chơi.
        // Tham số thứ 2 là "Barrier Action": Chạy khi cả 3 đã hội quân xong.
        CyclicBarrier barrier = new CyclicBarrier(3, () -> {
            System.out.println("\n[Hệ thống] Đã đủ 3 người! Đang khởi tạo trận đấu...");
        });

        // Mô phỏng 3 người chơi tham gia vào các thời điểm khác nhau
        for (int i = 1; i <= 3; i++) {
            String playerName = "Người chơi " + i;
            int finalI = i;
            new Thread(() -> {
                try {
                    System.out.println(playerName + " đang vào phòng chờ...");
                    Thread.sleep( (long) (finalI * 1000)); // Giả lập mạng lag khác nhau

                    System.out.println(playerName + " đã sẵn sàng và đang đợi đồng đội...");

                    // Điểm mấu chốt: Luồng dừng ở đây cho đến khi đủ 3 luồng gọi await()
                    barrier.await(timeWait, TimeUnit.SECONDS);

                    // Sau khi vượt qua Barrier, tất cả cùng chạy tiếp phần này
                    System.out.println(playerName + " ===> ĐÃ VÀO TRẬN!");
                } catch (InterruptedException e) {
                    e.printStackTrace();
                } catch (TimeoutException e) {
                    System.err.println("Player " + finalI + " Time out: Thời gian chờ ghép trận quá lâu . Huỷ");
                } catch (BrokenBarrierException e) {
                    System.err.println("Player " + finalI + " Broken : Không đủ người ghép trận . Huỷ");
                }
            }).start();

            Thread.sleep(10);
        }
    }

    public void cyclicBarrierReset() throws InterruptedException {
        // Rào chắn cho 2 xe bồn
        CyclicBarrier barrier = new CyclicBarrier(2, () -> {
            System.out.println("[Hệ thống] 2 xe đã nạp xong. Sẵn sàng phóng!");
        });

        // --- LƯỢT 1: Gặp sự cố ---
        System.out.println("--- BẮT ĐẦU LƯỢT NẠP 1 ---");
        Thread truck1 = createTruckThread("Xe bồn A", barrier, 1000); // Đến nhanh
        Thread truck2 = createTruckThread("Xe bồn B", barrier, 5000); // Đến cực chậm

        truck1.start();
        truck2.start();

        // Sau 2 giây, chỉ huy thấy quá lâu, quyết định RESET rào chắn để đổi đội xe khác
        Thread.sleep(2000);
        System.err.println("\n[Chỉ huy] Quá lâu! Hủy lượt này, Reset rào chắn...");
        barrier.reset();

        Thread.sleep(1000); // Đợi một chút để log in ra sạch sẽ

        // --- LƯỢT 2: Làm lại từ đầu ---
        System.out.println("\n--- BẮT ĐẦU LƯỢT NẠP 2 (Sau khi Reset) ---");
        // Barrier lúc này đã sạch sẽ như mới, có thể dùng tiếp ngay lập tức
        createTruckThread("Xe bồn C", barrier, 500).start();
        createTruckThread("Xe bồn D", barrier, 500).start();
    }

    public void semaphoreExample() {
        // Tổng cộng hệ thống có 5 slots
        Semaphore downloadSlots = new Semaphore(5);

        // Dùng 10 luồng để mô phỏng 10 yêu cầu tải file
        ExecutorService executor = Executors.newFixedThreadPool(10);

        for (int i = 1; i <= 10; i++) {
            final int fileId = i;
            executor.execute(() -> {
                String fileName = "Tài liệu_" + fileId;
                // Random số slot cần: 1 (thường) hoặc 2 (VIP/Nặng)
                int permitsRequired = (Math.random() > 0.7) ? 2 : 1;
                String type = (permitsRequired == 2) ? "[VIP-Heavy]" : "[Normal]";

                try {
                    System.out.println(String.format("%s %s đang đợi %d slot trống (chờ tối đa 2s)...",
                            type, fileName, permitsRequired));

                    // THAY ĐỔI CHÍNH: Thử xin 'permitsRequired' slots trong vòng 2 giây
                    if (downloadSlots.tryAcquire(permitsRequired, 2, TimeUnit.SECONDS)) {
                        try {
                            System.out.println(String.format("===> %s %s ĐÃ LẤY ĐƯỢC %d SLOT. Đang tải...",
                                    type, fileName, permitsRequired));

                            // Giả lập thời gian tải
                            Thread.sleep((long) (Math.random() * 3000 + 1000));

                            System.out.println(String.format("[Xong] V %s %s hoàn tất.", type, fileName));
                        } finally {
                            // QUAN TRỌNG: Trả lại đúng số lượng đã mượn
                            downloadSlots.release(permitsRequired);
                            System.out.println(String.format("--- %s đã giải phóng %d slot.", fileName, permitsRequired));
                        }
                    } else {
                        // Xử lý khi quá 2s mà không đủ slot
                        System.err.println(String.format("[Lỗi] X %s %s: Server quá tải/Không đủ slot, vui lòng thử lại!",
                                type, fileName));
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
        }

        executor.shutdown();
    }

    public void phaserExample() {
        // Khởi tạo Phaser với 1 bên tham gia là luồng Main để quản lý
        Phaser phaser = new Phaser(1);

        System.out.println("--- BẮT ĐẦU DÂY CHUYỀN LẮP RÁP ---");

        // Tạo 3 công nhân
        startWorker(phaser, "Công nhân CPU", true);   // Tham gia cả 2 phase
        startWorker(phaser, "Công nhân RAM", true);   // Tham gia cả 2 phase
        startWorker(phaser, "Công nhân NGUỒN", false); // Chỉ xong phase 0 là nghỉ

        // Main đợi Phase 0 hoàn thành
        int phase = phaser.getPhase();
        phaser.arriveAndAwaitAdvance();
        System.out.println("\n>>> [Hệ thống] Phase " + phase + " XONG: Phần cứng đã sẵn sàng.");

        // Main đợi Phase 1 hoàn thành
        phase = phaser.getPhase();
        phaser.arriveAndAwaitAdvance();
        System.out.println("\n>>> [Hệ thống] Phase " + phase + " XONG: Phần mềm đã cài xong.");

        // Kết thúc
        phaser.arriveAndDeregister();
        System.out.println("--- DÂY CHUYỀN KẾT THÚC ---");
    }

    private void startWorker(Phaser phaser, String name, boolean participateNextPhase) {
        phaser.register(); // Công nhân báo danh tham gia dây chuyền
        new Thread(() -> {
            // --- Phase 0 ---
            System.out.println(name + ": Đang lắp ráp phần cứng...");
            try { Thread.sleep((long) (Math.random() * 2000 + 500)); } catch (InterruptedException e) {}

            if (participateNextPhase) {
                System.out.println(name + ": Xong phần cứng. Đợi đồng đội để cài phần mềm...");
                phaser.arriveAndAwaitAdvance(); // Báo xong và đợi để sang phase sau

                // --- Phase 1 ---
                System.out.println(name + ": Đang cài đặt phần mềm...");
                try { Thread.sleep((long) (Math.random() * 2000 + 500)); } catch (InterruptedException e) {}
                System.out.println(name + ": Hoàn tất toàn bộ công việc.");
                phaser.arriveAndDeregister(); // Xong việc hoàn toàn, rời khỏi phaser
            } else {
                System.out.println(name + ": Xong phần cứng. Tôi nghỉ đây, không tham gia phase sau.");
                phaser.arriveAndDeregister(); // Báo xong và hủy đăng ký luôn
            }
        }).start();
    }

    private Thread createTruckThread(String name, CyclicBarrier barrier, int workTime) {
        return new Thread(() -> {
            try {
                System.out.println(name + " đang di chuyển vào vị trí...");
                Thread.sleep(workTime);
                System.out.println(name + " đã nạp xong, đang đợi đồng đội...");

                barrier.await(); // Đứng đợi ở rào chắn

                System.out.println(name + ": Tiếp tục nhiệm vụ khác.");
            } catch (BrokenBarrierException e) {
                System.err.println(name + ": !!! Báo động: Rào chắn bị RESET hoặc VỠ rồi, tôi đi về xưởng!");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
    }
}
