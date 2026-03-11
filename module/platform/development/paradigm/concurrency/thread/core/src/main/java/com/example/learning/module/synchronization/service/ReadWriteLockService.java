package com.example.learning.module.synchronization.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

@Service
public class ReadWriteLockService {
    private String content = "Thông báo trống";
    private final ReadWriteLock rwLock = new ReentrantReadWriteLock();

    public void readAndReadLock() throws InterruptedException {
        // CASE 1: ĐỌC - ĐỌC (Song song)
        System.out.println("=== TEST 1: ĐỌC - ĐỌC (Sẽ chạy cùng lúc) ===");
        Thread s1 = readNotice("Sinh viên A", "SV-A");
        Thread s2 = readNotice("Sinh viên B", "SV-B");
        Thread s3 = readNotice("Sinh viên C", "SV-C");
        s1.start(); s2.start(); s3.start();
        s1.join(); s2.join(); s3.join();
    }

    public void readAndWriteLock() throws InterruptedException {
        // CASE 2: ĐỌC - GHI (Chặn nhau)
        System.out.println("\n=== TEST 2: ĐỌC - GHI (Sinh viên phải đợi Thầy) ===");
        Thread teacher1 = postNotice("Thầy Hùng", "Nghỉ học ngày mai", "Thay-Hung");
        Thread s3 = readNotice("Sinh viên C", "SV-C");
        teacher1.start();
        Thread.sleep(100); // Đảm bảo Thầy lấy được WriteLock trước
        s3.start();
        teacher1.join(); s3.join();
    }

    public void writeAndWriteLock() {
        // CASE 3: GHI - GHI (Độc quyền hoàn toàn)
        System.out.println("\n=== TEST 3: GHI - GHI (Thầy này phải đợi Thầy kia) ===");
        Thread teacher2 = postNotice("Thầy Nam", "Học bù vào thứ 7", "Thay-Nam");
        Thread teacher3 = postNotice("Cô Lan", "Kiểm tra 15 phút", "Co-Lan");
        teacher2.start(); teacher3.start();
    }

    public void downgradeLock() throws InterruptedException {
        System.out.println("\n=== TEST 4: DOWNGRADING (Thầy dán xong giữ lại để xem, SV vẫn vào xem cùng được) ===");

        Thread teacher4 = postAndVerifyNotice("Thầy Bình", "Lịch thi học kỳ", "Thay-Binh");
        Thread s4 = readNotice("Sinh viên D", "SV-D");
        Thread teacher5 = postNotice("Cô Mai", "Thông báo họp phụ huynh", "Co-Mai");

        teacher4.start();
        Thread.sleep(500); // Đợi Thầy Bình dán xong và đang chuẩn bị hạ cấp
        s4.start();        // SV-D vào đọc (Sẽ vào được cùng lúc khi Thầy Bình đang giữ Read Lock)
        teacher5.start();  // Cô Mai muốn dán đè (Sẽ phải đợi Thầy Bình nhả hoàn toàn Read Lock)
    }

    // Phương thức ĐỌC
    private Thread readNotice(String studentName, String threadName) {

        return new Thread(() -> {
            try {
                if (rwLock.readLock().tryLock(1, TimeUnit.SECONDS)) {
                    try {
                        System.out.println("[READ] " + studentName + " đang bắt đầu đọc: " + content);
                        Thread.sleep(1500); // Giả lập đọc chậm để thấy sự song song
                        System.out.println("[READ] " + studentName + " đã đọc xong.");
                    } catch (InterruptedException e) {
                        System.err.println("Không thể đọc : Thông báo đang được chỉnh sửa");
                    } finally {
                        rwLock.readLock().unlock();
                    }
                } else {
                    System.err.println("[READ] : " + studentName + " chờ lâu quá . Huỷ việc đọc");
                }
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        },threadName);

    }

    // Phương thức GHI
    private Thread postNotice(String teacherName, String newContent,String threadName) {
        return new Thread(() -> {
            try {
                if (rwLock.writeLock().tryLock(1, TimeUnit.SECONDS)) {
                    try {
                        System.out.println("[WRITE] --- " + teacherName + " ĐANG DÁN THÔNG BÁO MỚI... ---");
                        Thread.sleep(2000); // Giả lập dán bảng tin mất thời gian
                        this.content = newContent;
                        System.out.println("[WRITE] --- " + teacherName + " ĐÃ DÁN XONG: " + content + " ---");
                    } catch (InterruptedException e) {
                        System.err.println("Không thể sửa : Thông báo đang được người khác sửa");
                    } finally {
                        rwLock.writeLock().unlock();
                    }
                } else {
                    System.err.println("[WRITE] : Chờ lâu quá " + teacherName + " huỷ việc dán thông báo");
                }
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        },threadName);
    }

    // Phương thức GHI kèm theo HẠ CẤP KHÓA (Downgrade)
    private Thread postAndVerifyNotice(String teacherName, String newContent, String threadName) {
        return new Thread(() -> {
            // 1. Lấy Write Lock để dán thông báo
            try {
                if (rwLock.writeLock().tryLock(1,TimeUnit.SECONDS)) {
                    try {
                        System.out.println("[WRITE] " + teacherName + " đang dán thông báo: " + newContent);
                        this.content = newContent;
                        Thread.sleep(1000); // Giả lập dán bảng

                        // 2. BẮT ĐẦU HẠ CẤP: Lấy Read Lock ngay khi vẫn đang giữ Write Lock
                        rwLock.readLock().lock();
                        System.out.println("[DOWNGRADE] " + teacherName + " đã lấy Read Lock thành công.");

                    } catch (InterruptedException e) {
                        System.err.println("Không thể sửa thông báo");
                    } finally {
                        // 3. Nhả Write Lock - Bây giờ Thầy chỉ còn giữ Read Lock
                        rwLock.writeLock().unlock();
                        System.out.println("[DOWNGRADE] " + teacherName + " đã nhả Write Lock. Hiện tại chỉ giữ Read Lock để kiểm tra.");
                    }
                }
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }

            try {
                // 4. Lúc này các Thầy/Cô khác không thể dán đè, nhưng các Sinh viên khác CÓ THỂ cùng vào đọc
                System.out.println("[VERIFY] " + teacherName + " đang kiểm tra lại nội dung vừa dán: " + content);
                Thread.sleep(2000); // Thầy kiểm tra kỹ trong 2 giây
            } catch (InterruptedException e) {
                System.err.println("Không thể kiểm tra lại thông báo");
            } finally {
                // 5. Cuối cùng mới nhả Read Lock
                rwLock.readLock().unlock();
                System.out.println("[FINISH] " + teacherName + " đã hài lòng và nhả Read Lock hoàn toàn.");
            }
        },threadName);
    }
}
