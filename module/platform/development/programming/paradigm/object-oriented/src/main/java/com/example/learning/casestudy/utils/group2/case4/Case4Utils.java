package com.example.learning.casestudy.utils.group2.case4;

import com.example.learning.casestudy.adapter.group2.case4.Client;
import com.example.learning.casestudy.adapter.group2.case4.FakeSocket;

public final class Case4Utils {
    public static void parse(boolean valid) {
        if (!valid) throw new IllegalArgumentException("Bad config");
    }
    public static Client broken(FakeSocket socket, boolean valid) {
        parse(valid);
        // Nếu lỗi: socket bị bỏ quên!
        return new Client(socket);
    }
    public static Client safe(FakeSocket socket, boolean valid) {
        try {
            parse(valid);
            return new Client(socket);
            // Ownership chỉ chuyển khi thành công.
        } catch (RuntimeException ex) {
            socket.close();
            throw ex;
        }
    }
    public static void check(boolean ok) {
        if (!ok) throw new AssertionError();
    }
    public static void reject(Runnable r) {
        try {
            r.run();
        } catch (IllegalArgumentException expected) {
            return;
        }
        throw new AssertionError("Expected bad config");
    }
}
